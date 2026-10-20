package com.aigooz.encyclopediaapp;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class QuizSessionTest {
    private DataModels.Quiz question(int points) {
        DataModels.Quiz quiz = new DataModels.Quiz(); quiz.q = "question"; quiz.points = points;
        quiz.options.addAll(Arrays.asList("wrong", "correct", "other")); quiz.answer = 1;
        return quiz;
    }

    @Test public void weightedPointsAreNotAccuracy() {
        QuizSession session = new QuizSession(); session.start(Arrays.asList(question(3), question(2)), "WARMA");
        assertEquals(5, session.maxScore());
        assertTrue(session.answer(session.questions.get(0).answer));
        assertFalse(session.answer(0)); session.next();
        session.answer((session.questions.get(1).answer + 1) % 3); session.next();
        assertTrue(session.complete()); assertEquals(1, session.correct()); assertEquals(50, session.accuracy());
        assertTrue(session.score() <= session.maxScore());
    }

    @Test public void shufflingPreservesAnswerAndDoesNotMutateSource() {
        DataModels.Quiz quiz = question(3);
        QuizSession session = new QuizSession(); session.start(Arrays.asList(quiz), "NUJIU");
        DataModels.Quiz shuffled = session.questions.get(0);
        assertEquals("correct", shuffled.options.get(shuffled.answer));
        assertEquals(Arrays.asList("wrong", "correct", "other"), quiz.options);
        assertFalse(session.answer(-1)); assertFalse(session.answer(3));
        session.answer(shuffled.answer); session.next();
        assertEquals(100, session.accuracy()); assertEquals(3, session.score());
        assertFalse(session.answer(0));
    }

    @Test public void roundTripRetainsAnsweredQuestionAndProgress() {
        QuizSession session = new QuizSession(); session.start(Arrays.asList(question(1), question(3)), "WARMA");
        session.answer(session.questions.get(0).answer);
        Gson gson = new Gson();
        QuizSession restored = gson.fromJson(gson.toJson(session), QuizSession.class);
        assertTrue(restored.answered()); assertEquals(1, restored.correct()); assertEquals("WARMA", restored.site);
        restored.next(); assertEquals(1, restored.index); assertFalse(restored.answered());
    }

    @Test public void invalidQuestionsAreExcluded() {
        DataModels.Quiz invalid = question(1); invalid.answer = 8;
        QuizSession session = new QuizSession(); session.start(Arrays.asList(invalid), "WARMA");
        assertTrue(session.questions.isEmpty()); assertEquals(0, session.accuracy());
        assertFalse(session.answer(0)); session.next(); assertEquals(0, session.index);
    }
}
