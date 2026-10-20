package com.aigooz.encyclopediaapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A saved round owns its questions so a data refresh cannot change its answers. */
public final class QuizSession {
    public List<DataModels.Quiz> questions = new ArrayList<>();
    public List<Integer> choices = new ArrayList<>();
    public int index;
    public String site = "";

    public static boolean valid(DataModels.Quiz quiz) {
        return quiz != null && quiz.q != null && !quiz.q.trim().isEmpty() && quiz.options != null
                && quiz.options.size() >= 2 && quiz.answer >= 0 && quiz.answer < quiz.options.size();
    }

    public void start(List<DataModels.Quiz> pool, String siteName) {
        questions.clear(); choices.clear(); index = 0; site = siteName;
        List<DataModels.Quiz> shuffled = new ArrayList<>();
        for (DataModels.Quiz quiz : pool) if (valid(quiz)) shuffled.add(quiz);
        Collections.shuffle(shuffled);
        for (DataModels.Quiz original : shuffled.subList(0, Math.min(10, shuffled.size()))) {
            DataModels.Quiz copy = new DataModels.Quiz();
            copy.id = original.id; copy.category = original.category; copy.difficulty = original.difficulty;
            copy.q = original.q; copy.explanation = original.explanation; copy.points = Math.max(1, original.points);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < original.options.size(); i++) order.add(i);
            Collections.shuffle(order);
            for (int i = 0; i < order.size(); i++) {
                copy.options.add(original.options.get(order.get(i)));
                if (order.get(i) == original.answer) copy.answer = i;
            }
            questions.add(copy);
        }
    }

    public boolean answered() { return index < choices.size(); }
    public boolean complete() { return !questions.isEmpty() && index >= questions.size(); }
    public boolean answer(int choice) {
        if (index >= questions.size() || answered() || choice < 0 || choice >= questions.get(index).options.size()) return false;
        choices.add(choice);
        return true;
    }
    public void next() { if (answered()) index++; }
    public int correct() {
        int count = 0;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i) == questions.get(i).answer) count++;
        return count;
    }
    public int score() {
        int points = 0;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i) == questions.get(i).answer) points += questions.get(i).points;
        return points;
    }
    public int maxScore() {
        int points = 0;
        for (DataModels.Quiz quiz : questions) points += quiz.points;
        return points;
    }
    public int accuracy() { return questions.isEmpty() ? 0 : correct() * 100 / questions.size(); }
}
