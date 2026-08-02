package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.ITEM_CREATE;
import static uk.gov.justice.laa.rcw.logging.LogAction.ITEM_DELETE;
import static uk.gov.justice.laa.rcw.logging.LogAction.ITEM_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.ITEM_LIST;
import static uk.gov.justice.laa.rcw.logging.LogAction.ITEM_UPDATE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.rcw.entity.ItemEntity;
import uk.gov.justice.laa.rcw.exception.ItemNotFoundException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ItemMapper;
import uk.gov.justice.laa.rcw.model.Item;
import uk.gov.justice.laa.rcw.model.ItemRequestBody;
import uk.gov.justice.laa.rcw.repository.ItemRepository;

/** Service class for handling items requests. */
@RequiredArgsConstructor
@Service
public class ItemService {

  private static final StructuredLogger log = StructuredLogger.of(ItemService.class);

  private final ItemRepository itemRepository;
  private final ItemMapper itemMapper;

  /**
   * Gets all items.
   *
   * @return the list of items
   */
  public List<Item> getAllItems() {
    List<Item> items = itemRepository.findAll().stream().map(itemMapper::toItem).toList();
    log.info().action(ITEM_LIST).outcome("success").log("Retrieved {} items", items.size());
    return items;
  }

  /**
   * Gets an item for a given id.
   *
   * @param id the item id
   * @return the requested item
   */
  public Item getItem(Long id) {
    ItemEntity itemEntity = checkIfItemExist(id);
    log.info()
        .action(ITEM_FETCH)
        .outcome("success")
        .with("item.id", id)
        .log("Retrieved item {}", id);
    return itemMapper.toItem(itemEntity);
  }

  /**
   * Creates an item.
   *
   * @param itemRequestBody the item to be created
   * @return the id of the created item
   */
  public Long createItem(ItemRequestBody itemRequestBody) {
    ItemEntity itemEntity = new ItemEntity();
    itemEntity.setName(itemRequestBody.getName());
    itemEntity.setDescription(itemRequestBody.getDescription());
    ItemEntity createdItemEntity = itemRepository.save(itemEntity);
    log.info()
        .action(ITEM_CREATE)
        .outcome("success")
        .with("item.id", createdItemEntity.getId())
        .log("Created item {}", createdItemEntity.getId());
    return createdItemEntity.getId();
  }

  /**
   * Updates an item.
   *
   * @param id the id of the item to be updated
   * @param itemRequestBody the updated item
   */
  public void updateItem(Long id, ItemRequestBody itemRequestBody) {
    ItemEntity itemEntity = checkIfItemExist(id);
    itemEntity.setName(itemRequestBody.getName());
    itemEntity.setDescription(itemRequestBody.getDescription());
    itemRepository.save(itemEntity);
    log.info()
        .action(ITEM_UPDATE)
        .outcome("success")
        .with("item.id", id)
        .log("Updated item {}", id);
  }

  /**
   * Deletes an item.
   *
   * @param id the id of the item to be deleted
   */
  public void deleteItem(Long id) {
    checkIfItemExist(id);
    itemRepository.deleteById(id);
    log.info()
        .action(ITEM_DELETE)
        .outcome("success")
        .with("item.id", id)
        .log("Deleted item {}", id);
  }

  private ItemEntity checkIfItemExist(Long id) {
    return itemRepository
        .findById(id)
        .orElseThrow(
            () -> new ItemNotFoundException(String.format("No item found with id: %s", id)));
  }
}
