import io
import itertools
import torch
import logging
from fastapi import FastAPI, Response
from fastapi.responses import PlainTextResponse

from neural_coref.run import Runner
from transformers import BertTokenizer, ElectraTokenizer
from transformers.models.bert import BasicTokenizer

from neural_coref.preprocess import get_document
from neural_coref.tensorize import Tensorizer
from neural_coref.conll import output_conll


SENTENCE_ENDERS = "!.?"
SUBSCRIPT = str.maketrans("0123456789", "₀₁₂₃₄₅₆₇₈₉")
DOC_NAME = "<document_name>"


class CorefHandler:
    def __init__(self):
        self.device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
        if torch.cuda.is_available():
            torch.cuda.empty_cache()  # clear GPU memory
        #self.runner = Runner("tuba10_electra_uncased", "0", skip_data_loading=True, log_to_file=False)
        #self.runner = Runner("tuba10_gelectra_large", "0", skip_data_loading=True, log_to_file=False)
        self.runner = Runner("se10_electra_uncased", "0", skip_data_loading=True, log_to_file=False)
        #self.runner = Runner("se10_gelectra_large", "0", skip_data_loading=True, log_to_file=False)
        #self.runner = Runner("droc_c2f", "0", skip_data_loading=True, log_to_file=False)
        #self.runner = Runner("droc_incremental_no_segment_distance", "0", skip_data_loading=True, log_to_file=False)
        self.model = self.runner.initialize_model()
        self.model.to(self.device)
        self.tensorizer = Tensorizer(self.runner.config)

        #state_dict = torch.load("models/model_tuba10_electra_uncased_Apr30_08-52-00_56879.bin", map_location=self.device)
        #state_dict = torch.load("models/model_tuba10_gelectra_large_Apr20_15-37-33_54802.bin", map_location=self.device)
        state_dict = torch.load("models/model_se10_electra_uncased_Apr12_16-08-17_42300.bin", map_location=self.device)
        #state_dict = torch.load("models/model_se10_gelectra_large_Apr12_17-08-33_36900.bin", map_location=self.device)
        #state_dict = torch.load("models/model_droc_c2f_May12_17-38-53_1800.bin", map_location=self.device)
        #state_dict = torch.load("models/model_droc_incremental_no_segment_distance_May02_17-32-58_1800.bin", map_location=self.device)

        self.model.load_state_dict(state_dict)
        self.model.eval()

        self.basic_tokenizer = BasicTokenizer(do_lower_case=False)
        if self.runner.config['model_type'] == 'electra':
            self.tokenizer = ElectraTokenizer.from_pretrained(self.runner.config['bert_tokenizer_name'], strip_accents=False)
        else:
            self.tokenizer = BertTokenizer.from_pretrained(self.runner.config['bert_tokenizer_name'])
        if self.runner.config['incremental']:
            self.tensorizer.long_doc_strategy = "keep"
        else:
            self.tensorizer.long_doc_strategy = "discard"

    def text_to_token_list(self, text):
        words = self.basic_tokenizer.tokenize(text)
        out = []
        sentence = []
        for word in words:
            sentence.append(word)
            if word in SENTENCE_ENDERS:
                out.append(sentence)
                sentence = []
        if len(sentence) > 0:
            out.append(sentence)
        return out

    def preprocess(self, tokenized_sentences: list):
        document = get_document('_', tokenized_sentences, 'german', 20, self.tokenizer, 'nested_list')
        _, example = self.tensorizer.tensorize_example(document, is_training=False)[0]
        token_map = self.tensorizer.stored_info['subtoken_maps']['_']
        # Remove gold
        tensorized = [torch.tensor(e) for e in example[:7]]

        #output_format = 'raw', 'conll', 'list'
        output_format = "list"
        return tensorized, (token_map, tokenized_sentences), output_format


    def postprocess(self, inference_output):
        # We only support a batch size of one!
        assert len(inference_output) == 1
        output, (token_map, tokenized_sentences), output_mode = inference_output[0]
        if self.runner.config['incremental']:
            span_starts, span_ends, mention_to_cluster_id, predicted_clusters = output
        else:
            _, _, _, span_starts, span_ends, antecedent_idx, antecedent_scores = output
            predicted_clusters, _, _ = self.model.get_predicted_clusters(
                span_starts.cpu().numpy(),
                span_ends.cpu().numpy(),
                antecedent_idx.cpu().numpy(),
                antecedent_scores.detach().cpu().numpy()
            )
        predicted_clusters_words = []
        for cluster in predicted_clusters:
            current_cluster = []
            for pair in cluster:
                current_cluster.append((token_map[pair[0]], token_map[pair[1]]))
            predicted_clusters_words.append(current_cluster)
        if output_mode == "raw":
            words = list(itertools.chain.from_iterable(tokenized_sentences))
            for cluster_id, cluster in enumerate(predicted_clusters):
                for pair in cluster:
                    words[pair[0]] = "[" + words[pair[0]]
                    words[pair[1]] = words[pair[1]] + "]" + str(cluster_id).translate(SUBSCRIPT)
            text = " ".join(words)
            # Pitiful attempt of fixing what whitespace tokenization removed
            # but its only meant for direct human usage, so it should be fine.
            for sentence_ender in SENTENCE_ENDERS + ",":
                text = text.replace(" " + sentence_ender, sentence_ender)
            return [text]
        elif output_mode == "conll":
            lines = [f"#begin document {DOC_NAME}"]
            for sentence_id, sentence in enumerate(tokenized_sentences, 1):
                for word_id, token in enumerate(sentence, 1):
                    line = ["memory_file", str(sentence_id), str(word_id), token] + ["-"] * 9
                    lines.append("\t".join(line))
                lines.append("\n")
            lines.append("#end document")
            input_file = io.StringIO(
                "\n".join(lines)
            )
            output_file = io.StringIO("")
            predictions = {
                DOC_NAME: predicted_clusters
            }
            token_maps = {
                DOC_NAME: token_map
            }
            output_conll(input_file, output_file, predictions, token_maps, False)
            return [output_file.getvalue()]
        else:
            return [predicted_clusters_words]

    def inference(self, data, *args, **kwargs):
        in_data, token_info, output_mode = data
        marshalled_data = [d.to(self.device) for d in in_data]
        with torch.no_grad():
            results = self.model(*marshalled_data, *args, **kwargs)
        # Batch size 1, so we wrap this into a batch list
        return [(results, token_info, output_mode)]


if __name__ == "__main__":
    handler = CorefHandler()
    data = [["Die", "Organisation", "gab", "bekannt", "sie", "habe", "Spenden", "veruntreut", "."],
            ["Sie", "ist", "sehr", "traurig", "!"],
            ["Und", "sie", "ist", "sehr", "betroffen", "!"]]

    #data = [['Die', 'Rhizocephala', 'tanzen', '.'],
    #        ['Der', 'Buntspecht', 'frisst', 'die', 'Rhizocephala', '.'],
    #        ['Die', 'Rhizocephala', 'erteilt', 'dem', 'Buntspecht', 'eine', 'Lektion', '.'],
    #        ['Rhizocephala', 'wurde', 'gefressen', '.'],
    #        ['Rhizocephala', 'wurde', 'nicht', 'gefressen', '.']]

    #data = handler.text_to_token_list("Die Rhizocephala tanzen. Der Buntspecht frisst die Rhizocephala. Die Rhizocephala erteilt dem Buntspecht eine Lektion. Rhizocephala wurde gefressen. Rhizocephala wurde nicht gefressen. ")

    preprocessed = handler.preprocess(data)
    print("preprocessed", preprocessed)
    inference_output = handler.inference(preprocessed)
    print("inference_output", inference_output)
    postprocessed = handler.postprocess(inference_output)
    print("postprocessed", postprocessed)


