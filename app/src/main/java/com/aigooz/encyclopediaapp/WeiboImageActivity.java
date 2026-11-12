package com.aigooz.encyclopediaapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

/**
 * 应用内图片查看器：左右滑动浏览同一条微博的配图，支持双指缩放。
 *
 * 全部在应用内完成，不跳转浏览器。
 */
public class WeiboImageActivity extends AppCompatActivity {
    private static final String EXTRA_URLS = "urls";
    private static final String EXTRA_INDEX = "index";

    static void open(android.content.Context context, ArrayList<String> urls, int index) {
        android.content.Intent intent = new android.content.Intent(context, WeiboImageActivity.class);
        intent.putStringArrayListExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_INDEX, index);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weibo_image);
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null) urls = new ArrayList<>();
        int start = getIntent().getIntExtra(EXTRA_INDEX, 0);

        MaterialToolbar toolbar = findViewById(R.id.imageToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        TextView counter = findViewById(R.id.imageCounter);
        ViewPager2 pager = findViewById(R.id.imagePager);
        pager.setAdapter(new ImageAdapter(urls));
        final int total = urls.size();
        pager.setCurrentItem(Math.max(0, Math.min(start, Math.max(0, total - 1))), false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                counter.setText((position + 1) + " / " + total);
            }
        });
        counter.setText((Math.min(start, Math.max(0, total - 1)) + 1) + " / " + total);
        toolbar.setTitle("配图");
    }

    private static class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
        private final ArrayList<String> urls;

        ImageAdapter(ArrayList<String> urls) { this.urls = urls; }

        @NonNull @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_weibo_zoom_image, parent, false);
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            holder.bind(urls.get(position));
        }

        @Override public int getItemCount() { return urls.size(); }
    }

    private static class ImageHolder extends RecyclerView.ViewHolder {
        private final ImageView image;

        ImageHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.zoomImage);
        }

        void bind(String url) {
            WeiboImages.load(image.getContext(), image, url, R.drawable.ic_avatar_placeholder);
        }
    }
}
