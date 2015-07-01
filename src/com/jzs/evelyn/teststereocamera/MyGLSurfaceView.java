package com.jzs.evelyn.teststereocamera;

import android.content.Context;
import android.opengl.GLSurfaceView;

class MyGLSurfaceView extends GLSurfaceView
{
    MyGL20Renderer renderer;
    public MyGLSurfaceView(Context context)
    {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new MyGL20Renderer((MainActivity)context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }
    public MyGL20Renderer getRenderer()
    {
        return renderer;
    }
}
