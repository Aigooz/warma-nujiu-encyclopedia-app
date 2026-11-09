package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.Holder> {
    private final Context context;
    private List<DataModels.Video> videos;
    private String metric;
    private final Consumer<DataModels.Video> listener;

    public RankingAdapter(Context context, List<DataModels.Video> videos, String metric, Consumer<DataModels.Video> listener) {
        this.context = context;
        this.videos = videos;
        this.metric = metric;
        this.listener = listener;
    }

    /**
     * 增量更新榜单内容与指标列。
     *
     * 榜单条目最多 10 条，直接全量 notifyDataSetChanged 的开销可以忽略；
     * 关键在于**复用同一个适配器实例**，这样 RecyclerView 的 ViewHolder 复用池与
     * 滚动位置都能保留（旧实现每次渲染都 setAdapter 新建，把复用池丢掉了）。
     */
    public void submit(List<DataModels.Video> updated, String newMetric) {
        this.videos = new java.util.ArrayList<>(updated);
        this.metric = newMetric;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DataModels.Video video = videos.get(position);
        holder.rank.setText(String.format(Locale.CHINA, "%02d", position + 1));
        holder.title.setText(video.title);
        holder.meta.setText(video.date + " · " + video.account);
        holder.value.setText(UiUtils.number(context, video.metric(metric)));
        holder.itemView.setOnClickListener(v -> listener.accept(video));
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView rank;
        TextView title;
        TextView meta;
        TextView value;

        Holder(View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.rank);
            title = itemView.findViewById(R.id.title);
            meta = itemView.findViewById(R.id.meta);
            value = itemView.findViewById(R.id.value);
        }
    }
}
