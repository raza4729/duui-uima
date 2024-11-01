package org.texttechnologylab.DockerUnifiedUIMAInterface.tools;

public class LocalSentence {
    private String id;
    private String sentence;
    private String label;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return "Sentence{" +
                "id='" + id + '\'' +
                ", sentence='" + sentence + '\'' +
                ", label='" + label + '\'' +
                '}';
    }
}
