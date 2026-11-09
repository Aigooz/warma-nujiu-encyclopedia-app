package com.aigooz.encyclopediaapp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 端到端校验「内置快照 → Gson 解析 → 数据模型」这条链路。
 *
 * 这个测试的价值在开启 R8 之后尤其明显：如果 proguard-rules.pro 里的 keep 规则
 * 失效导致字段被重命名，这里会直接失败，而不是等到用户装上新版才发现百科数据全空。
 *
 * 注意：单元测试运行在 JVM 上，会用 DataRepository 中同一套裁剪 + Gson 逻辑，
 * 因此能真实反映 release 构建下的解析行为。
 */
public class BundledDataParseTest {

    private DataModels.RawData parseData(String site) throws Exception {
        String raw = new String(
                Files.readAllBytes(Paths.get("src/main/assets/data_" + site + ".js")),
                StandardCharsets.UTF_8);
        Gson gson = new Gson();
        DataModels.RawData data = gson.fromJson(DataRepository.stripData(raw), DataModels.RawData.class);
        assertNotNull("解析结果不应为 null（字段名被改写的典型症状）", data);
        return data;
    }

    private List<DataModels.Quiz> parseQuiz(String site) throws Exception {
        String raw = new String(
                Files.readAllBytes(Paths.get("src/main/assets/quiz_" + site + ".js")),
                StandardCharsets.UTF_8);
        Gson gson = new Gson();
        return gson.fromJson(DataRepository.stripQuiz(raw),
                new TypeToken<List<DataModels.Quiz>>() {}.getType());
    }

    @Test public void warmaSnapshotSurvivesDeserialization() throws Exception {
        DataModels.RawData data = parseData("warma");
        assertFalse("视频列表不应为空", data.videos.isEmpty());
        assertFalse("docx_version 应被正确映射", data.docx_version.isEmpty());
        assertTrue("总播放应为正数", data.total_view > 0);
        assertFalse("年度弹幕映射失败", data.yearly_danmaku.isEmpty());
        assertFalse("UP 档案映射失败", data.profiles.isEmpty());
        assertFalse("收藏/粉丝数映射失败", data.followers.isEmpty());

        DataModels.Video first = data.videos.get(0);
        assertFalse("bvid 未映射", first.bvid.isEmpty());
        assertFalse("title 未映射", first.title.isEmpty());
        assertFalse("date 未映射", first.date.isEmpty());
        assertFalse("pic 未映射（封面会全部丢失）", first.pic.isEmpty());
        assertTrue("sub_lines 未映射", first.sub_lines >= 0);
        assertNotNull("tags 未映射", first.tags);
        assertNotNull("dm_profile 未映射（详情页曲线会空白）", first.dm_profile);
        assertNotNull("peaks 未映射", first.peaks);
        assertNotNull("memes 未映射", first.memes);
        assertNotNull("honors 未映射", first.honors);
    }

    @Test public void nujiuSnapshotSurvivesDeserialization() throws Exception {
        DataModels.RawData data = parseData("nujiu");
        assertFalse(data.videos.isEmpty());
        assertFalse(data.docx_version.isEmpty());
        DataModels.Video first = data.videos.get(0);
        assertFalse(first.bvid.isEmpty());
        assertFalse(first.title.isEmpty());
    }

    @Test public void nestedTypesKeepTheirFields() throws Exception {
        DataModels.RawData data = parseData("warma");
        DataModels.Video first = data.videos.get(0);

        // 嵌套集合若被 R8 破坏，这些断言会先炸。
        if (!first.tags.isEmpty()) {
            assertFalse("Tag.name 未映射", first.tags.get(0).name.isEmpty());
        }
        if (!first.peaks.isEmpty()) {
            DataModels.Peak peak = first.peaks.get(0);
            assertNotNull("Peak.samples 未映射", peak.samples);
        }
        if (!first.honors.isEmpty()) {
            DataModels.Honor honor = first.honors.get(0);
            assertNotNull("Honor.desc 未映射", honor.desc);
        }
        if (!first.memes.isEmpty()) {
            assertFalse("Meme.content 未映射", first.memes.get(0).content.isEmpty());
        }
        DataModels.Profile profile = data.profiles.values().iterator().next();
        assertFalse("Profile.name 未映射", profile.name.isEmpty());
        assertFalse("Profile.mid 未映射", profile.mid.isEmpty());
    }

    @Test public void memesAndYearlyAggregationWorkAfterParsing() throws Exception {
        DataModels.RawData data = parseData("warma");
        long memeTotal = 0;
        for (DataModels.Video video : data.videos) {
            for (DataModels.Meme meme : video.memes) memeTotal += (long) meme.count;
        }
        assertTrue("弹幕梗聚合应能取到数据", memeTotal > 0);
        long yearly = 0;
        for (Long value : data.yearly_danmaku.values()) yearly += value;
        assertTrue("年度弹幕聚合应能取到数据", yearly > 0);
    }

    @Test public void quizBanksSurviveDeserialization() throws Exception {
        for (String site : new String[]{"warma", "nujiu"}) {
            List<DataModels.Quiz> quizzes = parseQuiz(site);
            assertNotNull(site + ": 题库解析为 null", quizzes);
            assertFalse(site + ": 题库为空", quizzes.isEmpty());
            for (DataModels.Quiz quiz : quizzes) {
                assertTrue(site + ": 题目无效 id=" + quiz.id, QuizSession.valid(quiz));
                assertFalse(site + ": q 未映射", quiz.q.isEmpty());
                assertFalse(site + ": options 未映射", quiz.options.size() < 2);
                assertFalse(site + ": explanation 全部为空", quiz.explanation == null);
            }
        }
    }
}
