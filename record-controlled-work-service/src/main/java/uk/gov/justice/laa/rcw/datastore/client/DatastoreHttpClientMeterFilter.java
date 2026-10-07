package uk.gov.justice.laa.rcw.datastore.client;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;

final class DatastoreHttpClientMeterFilter implements MeterFilter {

  @Override
  public Meter.Id map(Meter.Id id) {
    if (!"http.client.requests".equals(id.getName())) {
      return id;
    }
    List<Tag> tags = id.getTags().stream().filter(tag -> !"error".equals(tag.getKey())).toList();
    return id.replaceTags(tags);
  }
}
