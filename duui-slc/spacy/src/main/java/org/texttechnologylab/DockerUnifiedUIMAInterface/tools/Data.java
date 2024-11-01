package org.texttechnologylab.DockerUnifiedUIMAInterface.tools;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public class Data {
    @JsonProperty("sentences")
    private List<LocalSentence> localSentences;

    public List<LocalSentence> getSentences() {
        return localSentences;
    }

    public void setSentences(List<LocalSentence> localSentences) {
        this.localSentences = localSentences;
    }

    @Override
    public String toString() {
        return "Data{" +
                "sentences=" + localSentences +
                '}';
    }
}