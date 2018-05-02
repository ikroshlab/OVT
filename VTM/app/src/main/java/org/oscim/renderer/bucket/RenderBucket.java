/*
 * Copyright 2012, 2013 Hannes Janetzek
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.renderer.bucket;

import java.nio.ShortBuffer;

import org.oscim.utils.pool.Inlist;


public abstract class RenderBucket extends Inlist<RenderBucket> {

	public static final int LINE = 0;
	public static final int TEXLINE = 1;
	public static final int POLYGON = 2;
	public static final int MESH = 3;
	public static final int EXTRUSION = 4;
	public static final int HAIRLINE = 5;
	public static final int SYMBOL = 6;
	public static final int BITMAP = 7;
	public static final int SDF = 8;

	public final int type;

	/** Drawing order from bottom to top. */
	int level;

	/** Number of vertices for this layer. */
	protected int numVertices;
	protected int numIndices;

	/** Temporary list of vertex data. */
	protected final VertexData vertexItems;
	protected final VertexData indiceItems;

	final static VertexData EMPTY = new VertexData();
	final boolean quads;

	protected RenderBucket(int type, boolean indexed, boolean quads) {
		this.type = type;
		vertexItems = new VertexData();
		if (indexed)
			indiceItems = new VertexData();
		else
			indiceItems = EMPTY;

		this.quads = quads;
	}

	/** Clear all resources. */
	protected void clear() {
		vertexItems.dispose();
		indiceItems.dispose();
		numVertices = 0;
		numIndices = 0;
	}

	/**
	 * Final preparation of content before compilation
	 * for stuff that should not be done on render-thread.
	 */
	protected void prepare() {

	}

	/**
	 * For line- and polygon-buckets this is the offset
	 * of VERTICES in its bucket.vbo.
	 * For all other types it is the byte offset in vbo.
	 * FIXME - always use byte offset?
	 */
	public int getVertexOffset() {
		return vertexOffset;
	}

	/**
	 * Start position in ibo for this bucket
	 */
	public int getIndiceOffset() {
		return indiceOffset;
	}

	public void setVertexOffset(int offset) {
		this.vertexOffset = offset;
	}

	protected int vertexOffset;

	protected int indiceOffset;

	protected void compile(ShortBuffer vboData, ShortBuffer iboData) {
		compileVertexItems(vboData);
		if (iboData != null)
			compileIndiceItems(iboData);
	}

	protected void compileVertexItems(ShortBuffer vboData) {
		/* keep offset of layer data in vbo */
		vertexOffset = vboData.position() * 2; // FIXME 2? - should be vertex stride / num shorts
		vertexItems.compile(vboData);
	}

	protected void compileIndiceItems(ShortBuffer iboData) {
		/* keep offset of layer data in vbo */
		if (indiceItems == null || indiceItems.empty())
			return;

		indiceOffset = iboData.position() * 2; // needs byte offset...
		indiceItems.compile(iboData);
	}
}
