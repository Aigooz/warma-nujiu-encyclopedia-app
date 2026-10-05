package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.function.Consumer;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
    private final Context context;
    private final List<DataModels.Video> videos;
    private final Consumer<DataModels.Video> listener;

    public VideoAdapter(Context context, List<DataModels.Video> videos, Consumer<DataModels.Video> listener) {
        this.context = context;
        this.videos = videos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DataModels.Video video = videos.get(position);
        holder.title.setText(video.title);
        holder.meta.setText(video.date + " · " + accountShort(video.account) + (video.type.isEmpty() ? "" : " · " + video.type));
        holder.stats.setText(UiUtils.number(context, video.view) + " 播放 · "
                + UiUtils.number(context, video.like) + " 赞 · "
                + UiUtils.number(context, video.danmaku) + " 弹幕");
        String cover = video.pic == null ? "" : video.pic.replace("http://", "https://");
        Glide.with(context).load(cover).centerCrop().placeholder(R.drawable.ic_video).into(holder.cover);
        holder.itemView.setOnClickListener(v -> listener.accept(video));
    }

    private String accountShort(String account) {
        if (account == null) return "";
        if (account.contains("主号")) return "主号";
        if (account.contains("小号")) return "小号";
        return account;
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title;
        TextView meta;
        TextView stats;
        com.google.android.material.imageview.ShapeableImageView cover;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            meta = itemView.findViewById(R.id.meta);
            stats = itemView.findViewById(R.id.stats);
            cover = itemView.findViewById(R.id.cover);
        }
    }
}
