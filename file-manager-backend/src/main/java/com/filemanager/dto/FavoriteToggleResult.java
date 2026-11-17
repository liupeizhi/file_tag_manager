package com.filemanager.dto;

import lombok.Data;

/**
 * 收藏切换结果。favorited 表示切换后的状态(已收藏为 true)。
 */
@Data
public class FavoriteToggleResult {

    private boolean favorited;
    private FavoriteDTO favorite;

    public static FavoriteToggleResult on(FavoriteDTO favorite) {
        FavoriteToggleResult r = new FavoriteToggleResult();
        r.setFavorited(true);
        r.setFavorite(favorite);
        return r;
    }

    public static FavoriteToggleResult off() {
        FavoriteToggleResult r = new FavoriteToggleResult();
        r.setFavorited(false);
        r.setFavorite(null);
        return r;
    }
}
