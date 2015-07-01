package com.jzs.evelyn.teststereocamera.gles;


import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

public class GLUtil {
    public static final String TAG = "CameraApp/GLUtil";
    public static float[] createIdentityMatrix() {
        float[] matrix = new float[16];
        Matrix.setIdentityM(matrix, 0);
        return matrix;
    }

    public static void dumpMatrix(String tag, float[] sm){
	
	}
    
    public static float[] createFullSquareVertex(float width, float height) {
        float vertices[]=new float[] {
            0,     0,      0,
            0,     height, 0,
            width, height, 0,

            width, height, 0,
            width, 0,      0,
            0,     0,      0,
        };
        
        return vertices;
    }
    
    /** 
     * Convert x to openGL 
     *  
     * @param x 
     *            Screen x offset top left 
     * @return Screen x offset top left in OpenGL 
     */  
    public static float toOpenGLX(float x, int screenWidth, float ratio) {  
        return -1.0f * ratio + toOpenGLWidth(x, screenWidth, ratio);  
    }  
     
    /** 
     * Convert y to openGL y 
     *  
     * @param y 
     *            Screen y offset top left 
     * @return Screen y offset top left in OpenGL 
     */  
    public static float toOpenGLY(float y, int screenHeight) {  
        return 1.0f - toOpenGLHeight(y, screenHeight);  
    }  
     
    /** 
     * Convert width to openGL width 
     *  
     * @param width 
     * @return Width in openGL 
     */  
    public static float toOpenGLWidth(float width, int screenWidth, float ratio) {  
        return 2.0f * (width / screenWidth) * ratio;
    }  
     
    /** 
     * Convert height to openGL height 
     *  
     * @param height 
     * @return Height in openGL 
     */  
    public static float toOpenGLHeight(float height, int screenHeight) {  
        return 2.0f * (height / screenHeight);  
    }  
     
    /** 
     * Convert x to screen x 
     *  
     * @param glX 
     *            openGL x 
     * @return screen x 
     */  
    public static float toScreenX(float glX, int screenWidth, float ratio) {  
        return toScreenWidth(glX - (-1 * ratio), screenWidth, ratio);  
    }  
     
    /** 
     * Convert y to screent y 
     *  
     * @param glY 
     *            openGL y 
     * @return screen y 
     */  
    public static float toScreenY(float glY, int screenHeight) {
        return toScreenHeight(1.0f - glY, screenHeight);
    }
     
    /** 
     * Convert glWidth to screen width 
     *  
     * @param glWidth 
     * @return Width in screen 
     */  
    public static float toScreenWidth(float glWidth, int screenWidth, float ratio) {  
        return (glWidth * screenWidth) / (2.0f * ratio);  
    }  
     
    /** 
     * Convert height to screen height 
     *  
     * @param glHeight 
     * @return Height in screen 
     */  
    public static float toScreenHeight(float glHeight, int screenHeight) {  
        return (glHeight * screenHeight) / 2.0f;
    }  

//    public static float[] createTopRightRect(int width, int height, float toTop) {
//        int minValue = Math.min(width, height);
//        //1.1f is workaround, when texture mapping, gird will occur
//        float topGrapihcEdge = (float)minValue * PIPCustomization.TOP_GRAPHIC_DEFAULT_EDGE_RELATIVE_VALUE * 1.1f;
//        float vertices[]=new float[] {
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE ,                         0 + toTop,                  0,
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE ,                         topGrapihcEdge + toTop,0,
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE + topGrapihcEdge ,        topGrapihcEdge + toTop,0,
//                
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE + topGrapihcEdge ,        topGrapihcEdge + toTop,0,
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE + topGrapihcEdge ,        0 + toTop,                 0,
//                (float)minValue * PIPCustomization.TOP_GRAPHIC_LEFT_TOP_RELATIVE_VALUE ,                         0 + toTop,                 0,
//          };
//        return vertices;
//    }
//
//    public static float[] createTopRightRect(AnimationRect rect) {
//        float vertices[]=new float[] {
//                rect.getLeftTop()[0],      rect.getLeftTop()[1], 0,
//                rect.getLeftBottom()[0],   rect.getLeftBottom()[1],0,
//                rect.getRightBottom()[0],  rect.getRightBottom()[1],0,
//
//                rect.getRightBottom()[0],  rect.getRightBottom()[1],0,
//                rect.getRightTop()[0],     rect.getRightTop()[1],0,
//                rect.getLeftTop()[0],      rect.getLeftTop()[1], 0,
//          };
//        return vertices;
//    }

    public static float[] createSquareVtxByCenterEdge(float centerX, float centerY, float edge) {
        float vertices[]=new float[] {
                (float)(centerX - (float)edge / 2),(float)(centerY - (float)edge / 2),0,
                (float)(centerX - (float)edge / 2),(float)(centerY + (float)edge / 2),0,
                (float)(centerX + (float)edge / 2),(float)(centerY + (float)edge / 2),0,

                (float)(centerX + (float)edge / 2),(float)(centerY + (float)edge / 2),0,
                (float)(centerX + (float)edge / 2),(float)(centerY - (float)edge / 2),0,
                (float)(centerX - (float)edge / 2),(float)(centerY - (float)edge / 2),0,
        };
        return vertices;
    }

    public static float[] createTexCoord() {
        float texCoor[]=new float[] {
                          0,0, 
                          0,1f, 
                          1f,1f,
                          
                          1f,1f, 
                          1f,0, 
                          0,0
          };  
          return texCoor;
    }

    private static float[] createHorizontalFlipTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                          highWidth,lowHeight, 
                          highWidth,highHeight, 
                          lowWidth,highHeight,
                          
                          lowWidth,highHeight, 
                          lowWidth,lowHeight, 
                          highWidth,lowHeight
          };  
          return texCoor;
    }

    public static float[] createTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight, boolean needHorizontalFlip) {
        if (needHorizontalFlip) {
            return createHorizontalFlipTexCoord(lowWidth, highWidth, lowHeight, highHeight);
        }
        return createTexCoord(lowWidth, highWidth, lowHeight, highHeight);
    }

    private static float[] createTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                lowWidth,lowHeight, 
                lowWidth,highHeight, 
                highWidth,highHeight,
                
                highWidth,highHeight, 
                highWidth,lowHeight, 
                lowWidth,lowHeight
        };  
        return texCoor;
    }
    // create 0 degree texture coordinate
    public static float[] createStandTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                          highWidth,lowHeight, 
                          highWidth,highHeight, 
                          lowWidth,highHeight,
                          
                          lowWidth,highHeight, 
                          lowWidth,lowHeight, 
                          highWidth,lowHeight
          };  
          return texCoor;
    }

    // create 180 degree texture coordinate
    public static float[] createReverseStandTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                lowWidth,highHeight, 
                lowWidth,lowHeight, 
                highWidth,lowHeight,
                
                highWidth,lowHeight, 
                highWidth,highHeight, 
                lowWidth,highHeight
        };
        return texCoor;
    }
    
    // create 90 degree texture coordinate
    public static float[] createRightTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                lowWidth,lowHeight, 
                highWidth,lowHeight, 
                highWidth,highHeight,
                
                highWidth,highHeight, 
                lowWidth,highHeight, 
                lowWidth,lowHeight
        };
        return texCoor;
    }
    
    // create 270 degree texture coordinate
    public static float[] createLeftTexCoord(float lowWidth, float highWidth, float lowHeight, float highHeight) {
        float texCoor[]=new float[] {
                highWidth,highHeight, 
                lowWidth,highHeight, 
                lowWidth,lowHeight,
                
                lowWidth,lowHeight, 
                highWidth,lowHeight, 
                highWidth,highHeight
        };
        return texCoor;
    }

    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,   vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
        	android.util.Log.e(TAG, "Could not link program:");
            android.util.Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
        	android.util.Log.e(TAG, "Could not compile shader(TYPE=" + shaderType + "):");
            android.util.Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        
        return shader;
    }

    public static void checkGlError(String op) {
        int error;
        while((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
        	android.util.Log.i(TAG, op + ":glGetError:0x" + Integer.toHexString(error));
            throw new RuntimeException("glGetError encountered (see log)");
        }
    }
    
    public static void checkEglError(String op) {
        int error;
        while((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
        	android.util.Log.e(TAG, op + ":eglGetError:0x" + Integer.toHexString(error));
            throw new RuntimeException("eglGetError encountered (see log)");
        }
    }

    public static int[] generateTextureIds(int num) {
    	android.util.Log.i(TAG, "GLUtil glGenTextures num = " + num);
        int[] textures = new int[num];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glGenTextures(num, textures, 0);
        int[] sizes= new int[2];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,sizes,0);
        android.util.Log.i(TAG, "GL_MAX_TEXTURE_SIZE sizes[0] = " + sizes[0] + " size[1] = " + sizes[1]);
        return textures;
    }

    public static void deleteTextures(int[] textureIds) {
        android.util.Log.i(TAG, "GLUtil glDeleteTextures num = " + textureIds.length);
        GLES20.glDeleteTextures(textureIds.length, textureIds, 0);
    }

    public static void bindPreviewTexure(int texId) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    public static void bindTexture(int texId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    }
}
