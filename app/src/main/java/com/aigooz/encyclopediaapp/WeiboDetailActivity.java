package com.aigooz.encyclopediaapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 微博详情：正文、配图与评论区全部在应用内渲染。
 *
 * 数据来自本地归档（data-weibo.js），因此即使微博过了"仅半年可见"期限、
 * 线上内容消失，这里依然能完整查看，不依赖浏览器或微博客户端。
 */
public class WeiboDetailActivity extends AppCompatActivity implements DataRepository.Listener {
    private Site site;
    private String postId;

    static void open(androidx.fragment.app.Fragment fragment, Site site, String postId) {
        android.content.Intent intent = new android.content.Intent(
                fragment.requireContext(), WeiboDetailActivity.class);
        intent.putExtra("site", site.name());
        intent.putExtra("post", postId);
        fragment.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weibo_detail);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        postId = getIntent().getStringExtra("post");
        site = MainActivity.parseSite(getIntent().getStringExtra("site"));
        if (site == null) site = MainActivity.currentSite();
        DataRepository.get().addListener(this);
        // load() 内部会 preload 全部站点，因此两位博主的归档都会就绪；
        // 这里只加载一次，避免反复切换 DataRepository 的当前站点。
        if (DataRepository.get().current(site) == null) {
            DataRepository.get().load(getApplicationContext(), site);
        }
        render();
    }

    @Override
    public void onDataChanged() {
        if (!isFinishing()) render();
    }

    @Override
    protected void onDestroy() {
        DataRepository.get().removeListener(this);
        super.onDestroy();
    }

    private void render() {
        LinearLayout container = findViewById(R.id.commentList);
        LinearLayout pics = findViewById(R.id.weiboPics);
        TextView meta = findViewById(R.id.weiboMeta);
        TextView text = findViewById(R.id.weiboText);
        TextView metrics = findViewById(R.id.weiboMetrics);
        TextView header = findViewById(R.id.commentHeader);

        DataModels.Dataset dataset = DataRepository.get().current(site);
        DataModels.WeiboPost post = findPost(dataset, postId);
        if (post == null) {
            text.setText("正在读取微博内容…");
            header.setVisibility(View.GONE);
            container.removeAllViews();
            pics.removeAllViews();
            return;
        }
        header.setVisibility(View.VISIBLE);
        meta.setText(post.date + (post.region == null || post.region.isEmpty() ? "" : " · " + post.region));
        text.setText(post.text);
        metrics.setText(String.format(java.util.Locale.CHINA,
                "赞 %s · 评论 %s · 转发 %s",
                UiUtils.number(this, post.likes),
                UiUtils.number(this, post.comments),
                UiUtils.number(this, post.reposts)));

        pics.removeAllViews();
        for (String url : post.pics) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (200 * getResources().getDisplayMetrics().density));
            params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            image.setLayoutParams(params);
            Glide.with(this).load(url.replace("http://", "https://")).into(image);
            pics.addView(image);
        }

        container.removeAllViews();
        header.setText("评论 · " + post.commentTotal() + " 条");
        if (post.comments_data.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂未收录这条微博的评论");
            empty.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
            container.addView(empty);
            return;
        }
        for (DataModels.WeiboComment comment : post.comments_data) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_weibo_comment, container, false);
            ((TextView) card.findViewById(R.id.commentUser)).setText(comment.user);
            ((TextView) card.findViewById(R.id.commentText)).setText(comment.text);
            ((TextView) card.findViewById(R.id.commentMeta))
                    .setText(commentMeta(comment.date, comment.region, comment.likes));
            TextView badge = card.findViewById(R.id.commentBadge);
            if (comment.is_author) {
                badge.setVisibility(View.VISIBLE);
                badge.setText("博主");
            }
            LinearLayout replies = card.findViewById(R.id.replyList);
            if (comment.replies == null || comment.replies.isEmpty()) {
                replies.setVisibility(View.GONE);
            } else {
                replies.setVisibility(View.VISIBLE);
                for (DataModels.WeiboReply reply : comment.replies) {
                    TextView row = new TextView(this);
                    String prefix = reply.is_author ? "[博主] " : "";
                    row.setText(prefix + reply.user + "：" + reply.text + "\n"
                            + reply.date + " · 赞 " + UiUtils.number(this, reply.likes));
                    row.setTextSize(12);
                    row.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                    row.setPadding(0, 6, 0, 6);
                    replies.addView(row);
                }
            }
            container.addView(card);
        }
    }

    private String commentMeta(String date, String region, double likes) {
        StringBuilder builder = new StringBuilder();
        if (date != null && !date.isEmpty()) builder.append(date);
        if (region != null && !region.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(region);
        }
        if (builder.length() > 0) builder.append(" · ");
        builder.append("赞 ").append(UiUtils.number(this, likes));
        return builder.toString();
    }

    private DataModels.WeiboPost findPost(DataModels.Dataset dataset, String id) {
        if (id == null) return null;
        // 微博列表会把两位博主合并展示，因此详情页也要跨站点查找，
        // 否则在怒九站点点开 Warma 的微博会读不到内容。
        for (Site candidate : Site.values()) {
            DataModels.Dataset source = dataset != null && dataset.site == candidate
                    ? dataset : DataRepository.get().current(candidate);
            if (source == null || source.data == null || source.data.weibo == null) continue;
            for (DataModels.WeiboAccount account : source.data.weibo.values()) {
                if (account == null || account.posts == null) continue;
                for (DataModels.WeiboPost post : account.posts) {
                    if (id.equals(post.id)) return post;
                }
            }
        }
        return null;
    }
}
