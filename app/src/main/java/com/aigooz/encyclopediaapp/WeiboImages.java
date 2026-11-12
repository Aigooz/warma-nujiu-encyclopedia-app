package com.aigooz.encyclopediaapp;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/**
 * 微博图片加载。
 *
 * 微博的图片 CDN（sinaimg.cn）会校验 Referer：不带 Referer 直接请求返回 403，
 * 因此这里统一补上微博站点的 Referer，头像与配图才能正常显示。
 */
final class WeiboImages {
    private WeiboImages() {}

    static void load(Context context, ImageView target, String url, int placeholder) {
        if (url == null || url.isEmpty()) {
            if (placeholder != 0) target.setImageResource(placeholder);
            return;
        }
        String httpsUrl = url.replace("http://", "https://");
        // 去掉会过期的签名参数，只保留稳定的资源路径。
        int query = httpsUrl.indexOf('?');
        if (query > 0) httpsUrl = httpsUrl.substring(0, query);
        GlideUrl glideUrl = new GlideUrl(httpsUrl, new LazyHeaders.Builder()
                .addHeader("Referer", "https://weibo.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                .build());
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                Glide.with(context).load(glideUrl);
        if (placeholder != 0) request = request.placeholder(placeholder);
        request.into(target);
    }
}
