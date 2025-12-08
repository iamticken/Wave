package org.whispersystems.waveservice.api.messages;


import java.util.Optional;

public class WaveServicePreview {
  private final String                            url;
  private final String                            title;
  private final String                            description;
  private final long                              date;
  private final Optional<WaveServiceAttachment> image;

  public WaveServicePreview(String url, String title, String description, long date, Optional<WaveServiceAttachment> image) {
    this.url         = url;
    this.title       = title;
    this.description = description;
    this.date        = date;
    this.image       = image;
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public long getDate() {
    return date;
  }

  public Optional<WaveServiceAttachment> getImage() {
    return image;
  }
}
