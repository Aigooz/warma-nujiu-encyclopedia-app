package com.aigooz.encyclopediaapp;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class VideoFilterTest {
    private DataModels.Video video(String bv, String date, String account, int lines, double views) {
        DataModels.Video video = new DataModels.Video();
        video.bvid = bv; video.date = date; video.account = account; video.sub_lines = lines; video.view = views;
        video.title = "测试 " + bv; video.type = "游戏";
        return video;
    }

    @Test public void combinesAccountYearTypeLinesAndFavorites() {
        DataModels.Video match = video("BV1", "2025-02-01", "Warma主号", 100, 20);
        List<DataModels.Video> source = Arrays.asList(match, video("BV2", "2025-01-01", "Warma小号", 500, 40),
                video("BV3", "2024-01-01", "Warma主号", 500, 80));
        VideoFilter filter = new VideoFilter();
        filter.account = "main"; filter.year = "2025"; filter.type = "游戏"; filter.minLines = 100; filter.favoritesOnly = true;
        assertEquals(Collections.singletonList(match), filter.apply(source, Collections.singleton("BV1")));
        filter.minLines = 101;
        assertTrue(filter.apply(source, Collections.singleton("BV1")).isEmpty());
        assertEquals(3, source.size());
    }

    @Test public void searchesTagsMemesAndBvWithoutCaseSensitivity() {
        DataModels.Video item = video("BVxyz", "2025-02-01", "主号", 1, 0);
        DataModels.Tag tag = new DataModels.Tag(); tag.name = "手工"; item.tags.add(tag);
        DataModels.Meme meme = new DataModels.Meme(); meme.content = "名场面"; item.memes.add(meme);
        VideoFilter filter = new VideoFilter();
        for (String query : Arrays.asList(" bvXYZ ", "手工", "名场面")) {
            filter.query = query;
            assertEquals(1, filter.apply(Collections.singletonList(item), Collections.emptySet()).size());
        }
        filter.query = "不存在";
        assertTrue(filter.apply(Collections.singletonList(item), Collections.emptySet()).isEmpty());
    }

    @Test public void sortsDeterministicallyAndResets() {
        DataModels.Video a = video("A", "2024-01-01", "主号", 50, 100);
        DataModels.Video b = video("B", "2025-01-01", "小号", 100, 200);
        DataModels.Video c = video("C", "2025-01-01", "主号", 100, 200);
        List<DataModels.Video> source = Arrays.asList(c, a, b);
        VideoFilter filter = new VideoFilter();
        assertEquals(Arrays.asList(b, c, a), filter.apply(source, Collections.emptySet()));
        filter.sort = "oldest";
        assertEquals(a, filter.apply(source, Collections.emptySet()).get(0));
        filter.sort = "lines";
        assertEquals(b, filter.apply(source, Collections.emptySet()).get(0));
        filter.query = "x"; filter.favoritesOnly = true; filter.reset();
        assertFalse(filter.isActive()); assertEquals("date", filter.sort);
        assertEquals("全部内容", filter.summary());
    }

    @Test public void durationSupportsNumericColonAndMissingValues() {
        DataModels.Video video = new DataModels.Video();
        video.duration = "73"; assertEquals("1:13", UiUtils.duration(video));
        video.duration = "1:02:03"; assertEquals(3723, UiUtils.durationSeconds(video), 0);
        video.duration = null; assertEquals("0:00", UiUtils.duration(video));
        video.duration_seconds = 3661; assertEquals("1:01:01", UiUtils.duration(video));
        assertEquals("0:00", UiUtils.timestamp(-1));
    }
}
