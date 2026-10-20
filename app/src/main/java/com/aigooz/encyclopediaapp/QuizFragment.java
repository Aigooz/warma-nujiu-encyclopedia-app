package com.aigooz.encyclopediaapp;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuizFragment extends Fragment implements DataRepository.Listener {
    private TextView quizCount;
    private ChipGroup categoryChips;
    private ChipGroup difficultyChips;
    private Button startButton;
    private LinearLayout quizArea;
    private String category = "全部";
    private String difficulty = "全部";
    private QuizSession session = new QuizSession();

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            category = state.getString("category", "全部");
            difficulty = state.getString("difficulty", "全部");
            String json = state.getString("session");
            if (json != null) session = new Gson().fromJson(json, QuizSession.class);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("category", category); state.putString("difficulty", difficulty);
        state.putString("session", new Gson().toJson(session));
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        quizCount = view.findViewById(R.id.quizCount);
        categoryChips = view.findViewById(R.id.categoryChips);
        difficultyChips = view.findViewById(R.id.difficultyChips);
        startButton = view.findViewById(R.id.startButton);
        quizArea = view.findViewById(R.id.quizArea);
        startButton.setOnClickListener(v -> {
            if (!session.questions.isEmpty() && !session.complete()) {
                new MaterialAlertDialogBuilder(requireContext()).setTitle("重新开始挑战？")
                        .setMessage("本轮已答 " + session.choices.size() + " 题，重新开始将清除本轮进度。")
                        .setNegativeButton("继续本轮", null).setPositiveButton("重新开始", (d, w) -> startQuiz()).show();
            } else startQuiz();
        });
        DataRepository.get().addListener(this);
        renderFilters(); showQuestion();
    }

    @Override public void onDataChanged() { if (getView() != null) renderFilters(); }
    @Override public void onDestroyView() { DataRepository.get().removeListener(this); super.onDestroyView(); }

    private List<DataModels.Quiz> filtered() {
        DataModels.Dataset dataset = DataRepository.get().current();
        List<DataModels.Quiz> result = new ArrayList<>();
        if (dataset == null || dataset.site != MainActivity.currentSite() || dataset.quiz == null) return result;
        for (DataModels.Quiz quiz : dataset.quiz) {
            if (QuizSession.valid(quiz) && (category.equals("全部") || category.equals(quiz.category))
                    && (difficulty.equals("全部") || difficulty.equals(quiz.difficulty))) result.add(quiz);
        }
        return result;
    }

    private void renderFilters() {
        if (!session.site.isEmpty() && !session.site.equals(MainActivity.currentSite().name())) {
            session = new QuizSession(); category = difficulty = "全部"; showQuestion();
        }
        categoryChips.removeAllViews(); difficultyChips.removeAllViews();
        DataModels.Dataset dataset = DataRepository.get().current();
        if (dataset == null || dataset.site != MainActivity.currentSite() || dataset.quiz == null || dataset.quiz.isEmpty()) {
            quizCount.setText(DataRepository.get().isLoading(MainActivity.currentSite()) ? "题库加载中…" : "暂无可用题目");
            startButton.setEnabled(false); return;
        }
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (DataModels.Quiz quiz : dataset.quiz) if (QuizSession.valid(quiz)) categories.put(quiz.category, categories.getOrDefault(quiz.category, 0) + 1);
        if (!category.equals("全部") && !categories.containsKey(category)) category = "全部";
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(categories.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        addChip(categoryChips, "全部", category, () -> { category = "全部"; renderFilters(); });
        for (Map.Entry<String, Integer> entry : sorted) addChip(categoryChips, entry.getKey(), category, () -> { category = entry.getKey(); renderFilters(); });
        String[][] levels = {{"全部", "全部"}, {"easy", "简单"}, {"medium", "普通"}, {"hard", "困难"}};
        for (String[] level : levels) addChip(difficultyChips, level[1], difficulty.equals("全部") ? "全部" : difficultyName(difficulty),
                () -> { difficulty = level[0]; renderFilters(); });
        int count = filtered().size();
        quizCount.setText("共 " + count + " 道题 · 数据版本 " + dataset.data.docx_version);
        startButton.setEnabled(count > 0);
        startButton.setText(!session.questions.isEmpty() && !session.complete() ? "重新开始" : "开始 " + Math.min(10, count) + " 题");
    }

    private void addChip(ChipGroup group, String text, String selected, Runnable select) {
        Chip chip = new Chip(requireContext()); chip.setText(text); chip.setCheckable(true);
        chip.setId(View.generateViewId()); chip.setChecked(text.equals(selected));
        chip.setOnClickListener(v -> select.run()); group.addView(chip);
    }

    private void startQuiz() {
        session.start(filtered(), MainActivity.currentSite().name()); renderFilters(); showQuestion();
    }

    private void showQuestion() {
        quizArea.removeAllViews();
        quizArea.setVisibility(session.questions.isEmpty() ? View.GONE : View.VISIBLE);
        if (session.questions.isEmpty()) return;
        if (session.complete()) { showResult(); return; }
        DataModels.Quiz quiz = session.questions.get(session.index);
        LinearProgressIndicator progress = new LinearProgressIndicator(requireContext());
        progress.setMax(session.questions.size()); progress.setProgress(session.choices.size());
        quizArea.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        addInsight(quiz.category + " · " + difficultyName(quiz.difficulty) + " · " + (session.index + 1) + "/" + session.questions.size(), quiz.q);
        int choice = session.answered() ? session.choices.get(session.index) : -1;
        for (int i = 0; i < quiz.options.size(); i++) {
            MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(quiz.options.get(i)); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setAllCaps(false); button.setMinHeight(dp(54)); button.setTextSize(15); button.setCornerRadius(dp(8));
            if (session.answered()) {
                button.setOnClickListener(v -> {});
                if (i == quiz.answer || i == choice) {
                    boolean correct = i == quiz.answer;
                    button.setIconResource(correct ? R.drawable.ic_check : R.drawable.ic_close);
                    int color = ContextCompat.getColor(requireContext(), correct ? R.color.primary : R.color.error_text);
                    button.setStrokeColor(ColorStateList.valueOf(color)); button.setTextColor(color); button.setIconTint(ColorStateList.valueOf(color));
                    button.setContentDescription(quiz.options.get(i) + (correct ? "，正确答案" : "，你的选择，回答错误"));
                } else button.setAlpha(0.55f);
            } else {
                int option = i;
                button.setOnClickListener(v -> { if (session.answer(option)) showQuestion(); });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6);
            quizArea.addView(button, params);
        }
        if (session.answered()) {
            addInsight(choice == quiz.answer ? "回答正确 · +" + quiz.points + " 分" : "回答错误", quiz.explanation == null ? "" : quiz.explanation);
            addAction(session.index == session.questions.size() - 1 ? "查看成绩" : "下一题", () -> { session.next(); showQuestion(); renderFilters(); });
        }
    }

    private void showResult() {
        addInsight("挑战完成 · 正确率 " + session.accuracy() + "%", "答对 " + session.correct() + "/" + session.questions.size()
                + " 题 · 积分 " + session.score() + "/" + session.maxScore());
        for (int i = 0; i < session.questions.size(); i++) {
            DataModels.Quiz quiz = session.questions.get(i);
            int choice = session.choices.get(i);
            if (choice != quiz.answer) addInsight("错题 " + (i + 1) + " · " + quiz.category, quiz.q + "\n\n你的答案：" + quiz.options.get(choice)
                    + "\n正确答案：" + quiz.options.get(quiz.answer) + "\n\n" + (quiz.explanation == null ? "" : quiz.explanation));
        }
        addAction("再来一轮", this::startQuiz);
    }

    private void addInsight(String heading, String text) {
        View card = getLayoutInflater().inflate(R.layout.item_insight, quizArea, false);
        ((TextView) card.findViewById(R.id.title)).setText(heading);
        TextView body = card.findViewById(R.id.body); body.setText(text); body.setTextSize(16);
        body.setTextIsSelectable(true); quizArea.addView(card);
    }

    private void addAction(String text, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext()); button.setText(text); button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12); quizArea.addView(button, params);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private String difficultyName(String value) { return "easy".equals(value) ? "简单" : "hard".equals(value) ? "困难" : "普通"; }
}
