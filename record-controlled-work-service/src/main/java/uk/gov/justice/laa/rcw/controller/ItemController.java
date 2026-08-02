package uk.gov.justice.laa.rcw.controller;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.rcw.api.ItemsApi;
import uk.gov.justice.laa.rcw.model.Item;
import uk.gov.justice.laa.rcw.model.ItemRequestBody;
import uk.gov.justice.laa.rcw.service.ItemService;

/** Controller for handling items requests. */
@RestController
@RequiredArgsConstructor
public class ItemController implements ItemsApi {
  private final ItemService service;

  @Override
  public ResponseEntity<List<Item>> getItems() {
    return ResponseEntity.ok(service.getAllItems());
  }

  @Override
  public ResponseEntity<Item> getItemById(Long id) {
    return ResponseEntity.ok(service.getItem(id));
  }

  @Override
  public ResponseEntity<Void> createItem(@RequestBody ItemRequestBody itemRequestBody) {
    Long id = service.createItem(itemRequestBody);
    URI uri =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  public ResponseEntity<Void> updateItem(Long id, ItemRequestBody itemRequestBody) {
    service.updateItem(id, itemRequestBody);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteItem(Long id) {
    service.deleteItem(id);
    return ResponseEntity.noContent().build();
  }
}
