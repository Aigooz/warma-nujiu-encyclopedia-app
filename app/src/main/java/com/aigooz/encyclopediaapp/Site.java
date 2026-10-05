package com.aigooz.encyclopediaapp;

public enum Site {
    WARMA("Warma 百科", "https://aigooz.github.io/warma-encyclopedia/data.js", "https://aigooz.github.io/warma-encyclopedia/data-quiz.js", "data_warma.js", "quiz_warma.js", "data_warma.json", "quiz_warma.json"),
    NUJIU("怒九百科", "https://aigooz.github.io/nujiu-encyclopedia/data.js", "https://aigooz.github.io/nujiu-encyclopedia/data-quiz.js", "data_nujiu.js", "quiz_nujiu.js", "data_nujiu.json", "quiz_nujiu.json");

    public final String title;
    public final String dataUrl;
    public final String quizUrl;
    public final String dataCacheName;
    public final String quizCacheName;
    public final String dataAssetName;
    public final String quizAssetName;

    Site(String title, String dataUrl, String quizUrl, String dataCacheName, String quizCacheName, String dataAssetName, String quizAssetName) {
        this.title = title;
        this.dataUrl = dataUrl;
        this.quizUrl = quizUrl;
        this.dataCacheName = dataCacheName;
        this.quizCacheName = quizCacheName;
        this.dataAssetName = dataAssetName;
        this.quizAssetName = quizAssetName;
    }
}
