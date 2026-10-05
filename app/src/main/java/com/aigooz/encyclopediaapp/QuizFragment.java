package com.aigooz.encyclopediaapp;

import android.content.res.ColorStateList;
import android.graphics.Color;
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
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
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
    private List<DataModels.Quiz> active = new ArrayList<>();
    private int index;
    private int score;
    private boolean answered;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        quizCount = view.findViewById(R.id.quizCount);
        categoryChips = view.findViewById(R.id.categoryChips);
        difficultyChips = view.findViewById(R.id.difficultyChips);
        startButton = view.findViewById(R.id.startButton);
        quizArea = view.findViewById(R.id.quizArea);
        startButton.setOnClickListener(v -> startQuiz());
        DataRepository.get().addListener(this);
        renderFilters();
    }

    @Override
    public void onDataChanged() {
        if (isAdded()) renderFilters();
    }

    @Override
    public void onDestroyView() {
        DataRepository.get().removeListener(this);
        super.onDestroyView();
    }

    private List<DataModels.Quiz> filtered() {
        DataModels.Dataset dataset = DataRepository.get().current();
        List<DataModels.Quiz> result = new ArrayList<>();
        if (dataset == null || dataset.site != MainActivity.currentSite() || dataset.quiz == null) return result;
        for (DataModels.Quiz quiz : dataset.quiz) {
            if ((category.equals("全部") || category.equals(quiz.category))
                    && (difficulty.equals("全部") || difficulty.equals(quiz.difficulty))) {
                result.add(quiz);
            }
        }
        return result;
    }

    private void renderFilters() {
        categoryChips.removeAllViews();
        difficultyChips.removeAllViews();
        DataModels.Dataset dataset = DataRepository.get().current();
        if (dataset == null || dataset.quiz == null || dataset.quiz.isEmpty()) {
            quizCount.setText("题库加载中…");
            return;
        }
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (DataModels.Quiz quiz : dataset.quiz) categories.put(quiz.category, categories.getOrDefault(quiz.category, 0) + 1);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(categories.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        addChip(categoryChips, "全部", category, id -> { category = id; renderFilters(); });
        for (Map.Entry<String, Integer> entry : sorted) {
            addChip(categoryChips, entry.getKey(), category, id -> { category = id; renderFilters(); });
        }
        String[][] difficulties = {{"全部", "全部"}, {"easy", "简单"}, {"medium", "普通"}, {"hard", "困难"}};
        String selectedDifficulty = "全部".equals(difficulty) ? "全部" : difficultyName(difficulty);
        for (String[] item : difficulties) {
            addChip(difficultyChips, item[1], selectedDifficulty, id -> {
                difficulty = id.equals("全部") ? "全部" : item[0];
                renderFilters();
            });
        }
        quizCount.setText("共 " + filtered().size() + " 道题 · 数据版本 " + dataset.data.docx_version);
    }

    private void addChip(ChipGroup group, String text, String selected, java.util.function.Consumer<String> onSelect) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setId(View.generateViewId());
        chip.setChecked(text.equals(selected) || (text.equals("全部") && selected.equals("全部")));
        chip.setOnClickListener(v -> onSelect.accept(text));
        group.addView(chip);
    }

    private void startQuiz() {
        List<DataModels.Quiz> pool = filtered();
        if (pool.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "当前筛选没有题目", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.shuffle(pool);
        active = new ArrayList<>(pool.subList(0, Math.min(10, pool.size())));
        index = 0;
        score = 0;
        quizArea.setVisibility(View.VISIBLE);
        showQuestion();
    }

    private void showQuestion() {
        quizArea.removeAllViews();
        if (index >= active.size()) {
            showResult();
            return;
        }
        DataModels.Quiz quiz = active.get(index);
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, quizArea, false);
        TextView title = card.findViewById(R.id.title);
        TextView body = card.findViewById(R.id.body);
        title.setText(quiz.category + " · " + difficultyName(quiz.difficulty) + " · " + (index + 1) + "/" + active.size() + " · 得分 " + score);
        body.setText(quiz.q);
        quizArea.addView(card);
        answered = false;
        LinearLayout optionWrap = new LinearLayout(requireContext());
        optionWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionWrap.setLayoutParams(wrapParams);
        for (int i = 0; i < quiz.options.size(); i++) {
            MaterialButton button = new MaterialButton(requireContext());
            button.setText(quiz.options.get(i));
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setAllCaps(false);
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F1FB")));
            button.setTextColor(Color.parseColor("#141830"));
            int optionIndex = i;
            button.setOnClickListener(v -> answer(optionWrap, button, optionIndex, quiz));
            optionWrap.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) button.getLayoutParams()).setMargins(0, (int) (getResources().getDisplayMetrics().density * 8), 0, 0);
        }
        quizArea.addView(optionWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void answer(LinearLayout wrap, MaterialButton selected, int choice, DataModels.Quiz quiz) {
        if (answered) return;
        answered = true;
        for (int i = 0; i < wrap.getChildCount(); i++) {
            MaterialButton button = (MaterialButton) wrap.getChildAt(i);
            button.setEnabled(false);
            if (i == quiz.answer) {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00B894")));
                button.setTextColor(Color.WHITE);
            } else if (button == selected) {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF6B8A")));
                button.setTextColor(Color.WHITE);
            } else {
                button.setAlpha(0.55f);
            }
        }
        if (choice == quiz.answer) score += quiz.points;
        TextView explanation = new TextView(requireContext());
        explanation.setText((choice == quiz.answer ? "回答正确！\n" : "回答错误。\n") + quiz.explanation);
        explanation.setTextColor(Color.parseColor("#656D7D"));
        explanation.setTextSize(13);
        explanation.setPadding(0, (int) (getResources().getDisplayMetrics().density * 12), 0, 0);
        quizArea.addView(explanation);
        MaterialButton next = new MaterialButton(requireContext());
        next.setText(index == active.size() - 1 ? "查看成绩" : "下一题");
        next.setOnClickListener(v -> {
            index++;
            showQuestion();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().density * 48));
        nextParams.topMargin = (int) (getResources().getDisplayMetrics().density * 14);
        quizArea.addView(next, nextParams);
    }

    private void showResult() {
        quizArea.removeAllViews();
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_insight, quizArea, false);
        TextView title = card.findViewById(R.id.title);
        TextView body = card.findViewById(R.id.body);
        int percent = score * 100 / Math.max(1, active.size());
        title.setText("挑战完成 · " + score + "/" + active.size());
        body.setText("正确率 " + percent + "%\n" + rankText(percent));
        quizArea.addView(card);
        MaterialButton restart = new MaterialButton(requireContext());
        restart.setText("再来一轮");
        restart.setOnClickListener(v -> startQuiz());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().density * 48));
        params.topMargin = (int) (getResources().getDisplayMetrics().density * 14);
        quizArea.addView(restart, params);
    }

    private String rankText(int percent) {
        if (percent >= 100) return "满分认证：你已经是百科本体了。";
        if (percent >= 90) return "词条级观众：几乎无可挑剔。";
        if (percent >= 75) return "资深考古家：细节掌握非常扎实。";
        if (percent >= 60) return "忠实观众：还能继续往深处挖掘。";
        if (percent >= 40) return "熟悉脸熟：不少名场面值得补课。";
        return "入门观众：适合从经典视频开始考古。";
    }

    private String difficultyName(String value) {
        if ("easy".equals(value)) return "简单";
        if ("hard".equals(value)) return "困难";
        return "普通";
    }
}
