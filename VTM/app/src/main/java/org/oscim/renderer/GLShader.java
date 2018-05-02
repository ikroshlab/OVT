package org.oscim.renderer;

import android.util.Log;

import static org.oscim.backend.GLAdapter.gl;

import java.nio.IntBuffer;

import org.oscim.backend.AssetAdapter;
import org.oscim.backend.GL;
import org.oscim.backend.GLAdapter;



public abstract class GLShader {

	public int program;

	protected boolean create(String vertexSource, String fragmentSource) {
		program = createProgram(vertexSource, fragmentSource);
		return program != 0;
	}

	protected boolean create(String fileName) {
		program = loadShader(fileName);
		return program != 0;
	}

	protected int getAttrib(String name) {
		int loc = gl.getAttribLocation(program, name);
		if (loc < 0) logDebug("missing attribute: " + name);
		return loc;
	}

	protected int getUniform(String name) {
		int loc = gl.getUniformLocation(program, name);
		if (loc < 0) logDebug("missing uniform: " + name);
		return loc;
	}

	public boolean useProgram() {
		return GLState.useProgram(program);
	}

	public static int loadShader(String file) {
		String path = "shaders/" + file + ".glsl";
		String vs = AssetAdapter.readTextFile(path);

		if (vs == null)
			throw new IllegalArgumentException("shader file not found: " + path);

		// TODO ...
		int fsStart = vs.indexOf('$');
		if (fsStart < 0 || vs.charAt(fsStart + 1) != '$')
			throw new IllegalArgumentException("not a shader file " + path);

		String fs = vs.substring(fsStart + 2);
		vs = vs.substring(0, fsStart);

		int shader = createProgram(vs, fs);
		if (shader == 0) {
			System.out.println(vs + " \n\n" + fs);
		}
		return shader;
	}

	public static int loadShader(int shaderType, String source) {

		int shader = gl.createShader(shaderType);
		if (shader != 0) {
			gl.shaderSource(shader, source);
			gl.compileShader(shader);
			IntBuffer compiled = MapRenderer.getIntBuffer(1);

			gl.getShaderiv(shader, GL.COMPILE_STATUS, compiled);
			compiled.position(0);
			if (compiled.get() == 0) {
				logDebug("Could not compile shader " + shaderType + ":");
				logDebug(gl.getShaderInfoLog(shader));
				gl.deleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}

	public static int createProgram(String vertexSource, String fragmentSource) {
		String defs = "";
		if (GLAdapter.GDX_DESKTOP_QUIRKS)
			defs += "#define DESKTOP_QUIRKS 1\n";
		else
			defs += "#define GLES 1\n";

		int vertexShader = loadShader(GL.VERTEX_SHADER, defs + vertexSource);
		if (vertexShader == 0) {
			return 0;
		}

		int pixelShader = loadShader(GL.FRAGMENT_SHADER, defs + fragmentSource);
		if (pixelShader == 0) {
			return 0;
		}

		int program = gl.createProgram();
		if (program != 0) {
			GLUtils.checkGlError("glCreateProgram");
			gl.attachShader(program, vertexShader);
			GLUtils.checkGlError("glAttachShader");
			gl.attachShader(program, pixelShader);
			GLUtils.checkGlError("glAttachShader");
			gl.linkProgram(program);
			IntBuffer linkStatus = MapRenderer.getIntBuffer(1);
			gl.getProgramiv(program, GL.LINK_STATUS, linkStatus);
			linkStatus.position(0);
			if (linkStatus.get() != GL.TRUE) {
				logDebug("Could not link program: ");
				logDebug(gl.getProgramInfoLog(program));
				gl.deleteProgram(program);
				program = 0;
			}
		}
		return program;
	}



    // handling Logging:
    private static void logDebug(String msg) { Log.d("VTM", " ### " + "GLShader" + " ###  " + msg); }
}
