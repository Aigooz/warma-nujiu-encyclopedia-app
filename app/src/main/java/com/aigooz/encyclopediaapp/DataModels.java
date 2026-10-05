package com.aigooz.encyclopediaapp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataModels {
    private DataModels() {}

    public static class RawData {
        public String docx_version = "";
        public long total_sub_lines;
        public long total_view;
        public long total_like;
        public long total_danmaku;
        public long total_coin;
        public long total_reply;
        public long total_favorite;
        public long total_share;
        public double total_duration;
        public Map<String, Long> yearly_danmaku = new LinkedHashMap<>();
        public List<Meme> top_memes = new ArrayList<>();
        public String insights_generated_at = "";
        public Map<String, Follower> followers = new LinkedHashMap<>();
        public Map<String, Profile> profiles = new LinkedHashMap<>();
        public List<Video> videos = new ArrayList<>();
    }

    public static class Profile {
        public String mid = "";
        public String name = "";
        public String sign = "";
        public long fans;
        public String face = "";
        public String avatar = "";
        public int level;
    }

    public static class Follower {
        public String name = "";
        public long follower;
    }

    public static class Video {
        public long no;
        public String title = "";
        public String date = "";
        public String pubdate_iso = "";
        public String type = "";
        public String account = "";
        public String account_short = "";
        public long sub_lines;
        public long sub_chars;
        public String bvid = "";
        public String aid = "";
        public String duration = "";
        public double duration_seconds;
        public String pic = "";
        public String desc = "";
        public String summary = "";
        public List<Tag> tags = new ArrayList<>();
        public List<List<Double>> dm_profile = new ArrayList<>();
        public List<Peak> peaks = new ArrayList<>();
        public List<Meme> memes = new ArrayList<>();
        public List<Honor> honors = new ArrayList<>();
        public double view;
        public double danmaku;
        public double reply;
        public double favorite;
        public double coin;
        public double share;
        public double like;
        public double gap_days;

        public double metric(String key) {
            switch (key) {
                case "like": return like;
                case "danmaku": return danmaku;
                case "coin": return coin;
                case "favorite": return favorite;
                case "reply": return reply;
                case "share": return share;
                default: return view;
            }
        }

        public double interactionRate() {
            return view <= 0 ? 0 : (like + coin + favorite + share + reply + danmaku) * 100.0 / view;
        }
    }

    public static class Tag {
        public long id;
        public String name = "";
    }

    public static class Peak {
        public double t;
        public double count;
        public List<String> samples = new ArrayList<>();
    }

    public static class Meme {
        public String content = "";
        public double count;
    }

    public static class Honor {
        public int type;
        public String desc = "";
    }

    public static class Quiz {
        public long id;
        public String category = "";
        public String difficulty = "normal";
        public String format = "mc";
        public int points = 1;
        public String q = "";
        public List<String> options = new ArrayList<>();
        public int answer;
        public String explanation = "";
    }

    public static class Dataset {
        public final Site site;
        public final RawData data;
        public final List<Quiz> quiz;
        public final boolean fromCache;

        public Dataset(Site site, RawData data, List<Quiz> quiz, boolean fromCache) {
            this.site = site;
            this.data = data;
            this.quiz = quiz;
            this.fromCache = fromCache;
        }
    }
}
