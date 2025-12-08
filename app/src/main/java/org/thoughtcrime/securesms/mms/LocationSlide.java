package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.location.WavePlace;

import java.util.Optional;


public class LocationSlide extends ImageSlide {

  @NonNull
  private final WavePlace place;

  public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull WavePlace place)
  {
    super(context, uri, size, 0, 0, null);
    this.place = place;
  }

  @Override
  @NonNull
  public Optional<String> getBody() {
    return Optional.of(place.getDescription());
  }

  @NonNull
  public WavePlace getPlace() {
    return place;
  }

  @Override
  public boolean hasLocation() {
    return true;
  }

}
