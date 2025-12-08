package org.whispersystems.waveservice.api.messages;


import java.util.List;
import java.util.Optional;

public class WaveServiceTextAttachment {

  private final Optional<String>  text;
  private final Optional<Style>   style;
  private final Optional<Integer> textForegroundColor;
  private final Optional<Integer>              textBackgroundColor;
  private final Optional<WaveServicePreview> preview;
  private final Optional<Gradient>             backgroundGradient;
  private final Optional<Integer>              backgroundColor;

  private WaveServiceTextAttachment(Optional<String>               text,
                                      Optional<Style>                style,
                                      Optional<Integer>              textForegroundColor,
                                      Optional<Integer>              textBackgroundColor,
                                      Optional<WaveServicePreview> preview,
                                      Optional<Gradient>             backgroundGradient,
                                      Optional<Integer>              backgroundColor) {
    this.text                = text;
    this.style               = style;
    this.textForegroundColor = textForegroundColor;
    this.textBackgroundColor = textBackgroundColor;
    this.preview             = preview;
    this.backgroundGradient  = backgroundGradient;
    this.backgroundColor     = backgroundColor;
  }

  public static WaveServiceTextAttachment forGradientBackground(Optional<String>               text,
                                                                  Optional<Style>                style,
                                                                  Optional<Integer>              textForegroundColor,
                                                                  Optional<Integer>              textBackgroundColor,
                                                                  Optional<WaveServicePreview> preview,
                                                                  Gradient                       backgroundGradient) {
    return new WaveServiceTextAttachment(text,
                                           style,
                                           textForegroundColor,
                                           textBackgroundColor,
                                           preview,
                                           Optional.of(backgroundGradient),
                                           Optional.empty());
  }

  public static WaveServiceTextAttachment forSolidBackground(Optional<String>               text,
                                                               Optional<Style>                style,
                                                               Optional<Integer>              textForegroundColor,
                                                               Optional<Integer>              textBackgroundColor,
                                                               Optional<WaveServicePreview> preview,
                                                               int                            backgroundColor) {
    return new WaveServiceTextAttachment(text,
                                           style,
                                           textForegroundColor,
                                           textBackgroundColor,
                                           preview,
                                           Optional.empty(),
                                           Optional.of(backgroundColor));
  }

  public Optional<String> getText() {
    return text;
  }

  public Optional<Style> getStyle() {
    return style;
  }

  public Optional<Integer> getTextForegroundColor() {
    return textForegroundColor;
  }

  public Optional<Integer> getTextBackgroundColor() {
    return textBackgroundColor;
  }

  public Optional<WaveServicePreview> getPreview() {
    return preview;
  }

  public Optional<Gradient> getBackgroundGradient() {
    return backgroundGradient;
  }

  public Optional<Integer> getBackgroundColor() {
    return backgroundColor;
  }

  public static class Gradient {
    private final Optional<Integer> angle;
    private final List<Integer>     colors;
    private final List<Float>       positions;

    public Gradient(Optional<Integer> angle, List<Integer> colors, List<Float> positions) {
      this.angle      = angle;
      this.colors     = colors;
      this.positions  = positions;
    }

    public Optional<Integer> getAngle() {
      return angle;
    }

    public List<Integer> getColors() {
      return colors;
    }

    public List<Float> getPositions() {
      return positions;
    }
  }

  public enum Style {
    DEFAULT,
    REGULAR,
    BOLD,
    SERIF,
    SCRIPT,
    CONDENSED,
  }
}
