package com.aigooz.encyclopediaapp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class BundledDataTest {
    @Test public void bothSitesSupportFiltersDurationsAndFullRounds() throws Exception {
        Gson gson = new Gson();
        for (String site : new String[]{"warma", "nujiu"}) {
            String raw = new String(Files.readAllBytes(Paths.get("src/main/assets/data_" + site + ".js")), StandardCharsets.UTF_8);
            String json = raw.substring(raw.indexOf('=') + 1).trim();
            if (json.endsWith(";")) json = json.substring(0, json.length() - 1);
            DataModels.RawData data = gson.fromJson(json, DataModels.RawData.class);
            assertFalse(data.videos.isEmpty());
            Set<String> ids = new HashSet<>();
            VideoFilter filter = new VideoFilter();
            assertEquals(data.videos.size(), filter.apply(data.videos, Collections.emptySet()).size());
            for (DataModels.Video video : data.videos) {
                assertTrue(site + ": duplicate BV " + video.bvid, ids.add(video.bvid));
                assertTrue(UiUtils.durationSeconds(video) >= 0);
            }
            filter.account = "main";
            for (DataModels.Video video : filter.apply(data.videos, Collections.emptySet())) assertTrue(video.account.contains("主号"));
            String quizText = new String(Files.readAllBytes(Paths.get("src/main/assets/quiz_" + site + ".js")), StandardCharsets.UTF_8);
            String quizJson = quizText.substring(quizText.indexOf('[', quizText.indexOf("QUIZ_DATA")), quizText.lastIndexOf(']') + 1);
            List<DataModels.Quiz> quizzes = gson.fromJson(quizJson, new TypeToken<List<DataModels.Quiz>>() {}.getType());
            assertFalse(quizzes.isEmpty());
            for (DataModels.Quiz quiz : quizzes) assertTrue(site + ": invalid question " + quiz.id, QuizSession.valid(quiz));
            QuizSession session = new QuizSession(); session.start(quizzes, site);
            assertEquals(10, session.questions.size());
            while (!session.complete()) { session.answer(session.questions.get(session.index).answer); session.next(); }
            assertEquals(100, session.accuracy()); assertEquals(session.maxScore(), session.score());
        }
    }
}
