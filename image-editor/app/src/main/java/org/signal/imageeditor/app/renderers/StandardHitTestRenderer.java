package org.wave.imageeditor.app.renderers;

import org.wave.imageeditor.core.Bounds;
import org.wave.imageeditor.core.Renderer;

public abstract class StandardHitTestRenderer implements Renderer {

  @Override
  public boolean hitTest(float x, float y) {
    return Bounds.contains(x, y);
  }
}
