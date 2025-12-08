/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wave.glide.load.resource.apng;

import android.content.Context;

import org.wave.glide.common.FrameAnimationDrawable;
import org.wave.glide.common.decode.FrameSeqDecoder;
import org.wave.glide.common.loader.AssetStreamLoader;
import org.wave.glide.common.loader.FileLoader;
import org.wave.glide.common.loader.Loader;
import org.wave.glide.common.loader.ResourceStreamLoader;
import org.wave.glide.load.resource.apng.decode.APNGDecoder;

/**
 * @Description: APNGDrawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
public class APNGDrawable extends FrameAnimationDrawable<APNGDecoder> {
    public APNGDrawable(Loader provider) {
        super(provider);
    }

    public APNGDrawable(APNGDecoder decoder) {
        super(decoder);
    }

    @Override
    protected APNGDecoder createFrameSeqDecoder(Loader streamLoader, FrameSeqDecoder.RenderListener listener) {
        return new APNGDecoder(streamLoader, listener);
    }


    public static APNGDrawable fromAsset(Context context, String assetPath) {
        AssetStreamLoader assetStreamLoader = new AssetStreamLoader(context, assetPath);
        return new APNGDrawable(assetStreamLoader);
    }

    public static APNGDrawable fromFile(String filePath) {
        FileLoader fileLoader = new FileLoader(filePath);
        return new APNGDrawable(fileLoader);
    }

    public static APNGDrawable fromResource(Context context, int resId) {
        ResourceStreamLoader resourceStreamLoader = new ResourceStreamLoader(context, resId);
        return new APNGDrawable(resourceStreamLoader);
    }
}
