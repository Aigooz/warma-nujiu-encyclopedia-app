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

import java.util.ArrayList;

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
        DataModels.WeiboAccount owner = findOwner(dataset, postId);
        String author = owner == null || owner.name == null || owner.name.isEmpty()
                ? "" : owner.name;
        ((TextView) findViewById(R.id.detailAuthor)).setText(author);
        WeiboImages.load(this, findViewById(R.id.detailAvatar),
                owner == null ? "" : owner.avatar, R.drawable.ic_avatar_placeholder);
        meta.setText(post.date + (post.region == null || post.region.isEmpty() ? "" : " · " + post.region));
        text.setText(post.text);
        ((TextView) findViewById(R.id.weiboReposts)).setText("转发 " + UiUtils.number(this, post.reposts));
        ((TextView) findViewById(R.id.weiboComments)).setText("评论 " + post.commentTotal());
        ((TextView) findViewById(R.id.weiboLikes)).setText("赞 " + UiUtils.number(this, post.likes));

        pics.removeAllViews();
        addImageGrid(pics, post.pics);

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
            WeiboImages.load(this, card.findViewById(R.id.commentAvatar),
                    comment.avatar, R.drawable.ic_avatar_placeholder);
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

    /** 与列表一致的三列配图网格，点开进入应用内看图。 */
    private void addImageGrid(LinearLayout container, java.util.List<String> images) {
        if (images == null || images.isEmpty()) return;
        int dp = (int) getResources().getDisplayMetrics().density;
        int columns = images.size() == 1 ? 1 : images.size() <= 4 ? 2 : 3;
        int limit = Math.min(images.size(), 9);
        int gap = 5 * dp;
        for (int start = 0; start < limit; start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (start > 0) rowParams.topMargin = gap;
            row.setLayoutParams(rowParams);
            for (int column = 0; column < columns; column++) {
                int index = start + column;
                if (index >= limit) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                    if (column > 0) spacerParams.setMarginStart(gap);
                    spacer.setLayoutParams(spacerParams);
                    row.addView(spacer);
                    continue;
                }
                View tile = LayoutInflater.from(this).inflate(R.layout.item_weibo_image, row, false);
                int height = columns == 1 ? 240 * dp : (columns == 2 ? 150 * dp : 110 * dp);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, height, 1f);
                if (column > 0) tileParams.setMarginStart(gap);
                tile.setLayoutParams(tileParams);
                ImageView image = tile.findViewById(R.id.weiboImage);
                WeiboImages.load(this, image, images.get(index), 0);
                final int position = index;
                tile.setOnClickListener(v -> WeiboImageActivity.open(
                        this, new ArrayList<>(images), position));
                row.addView(tile);
            }
            container.addView(row);
        }
    }

    private DataModels.WeiboAccount findOwner(DataModels.Dataset dataset, String id) {
        if (id == null) return null;
        for (Site candidate : Site.values()) {
            DataModels.Dataset source = dataset != null && dataset.site == candidate
                    ? dataset : DataRepository.get().current(candidate);
            if (source == null || source.data == null || source.data.weibo == null) continue;
            for (DataModels.WeiboAccount account : source.data.weibo.values()) {
                if (account == null || account.posts == null) continue;
                for (DataModels.WeiboPost post : account.posts) {
                    if (id.equals(post.id)) return account;
                }
            }
        }
        return null;
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
