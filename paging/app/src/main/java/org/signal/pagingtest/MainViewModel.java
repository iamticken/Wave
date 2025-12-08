package org.wave.pagingtest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.wave.paging.LivePagedData;
import org.wave.paging.PagingController;
import org.wave.paging.PagingConfig;
import org.wave.paging.PagedData;

import java.util.List;

public class MainViewModel extends ViewModel {

  private final LivePagedData<String, Item> pagedData;
  private final MainDataSource              dataSource;

  public MainViewModel() {
    this.dataSource = new MainDataSource(1000);
    this.pagedData = PagedData.createForLiveData(dataSource, new PagingConfig.Builder().setBufferPages(3)
                                                                                       .setPageSize(25)
                                                                                       .build());
  }

  public void onItemClicked(@NonNull String key) {
    dataSource.updateItem(key);
    pagedData.getController().onDataItemChanged(key);
  }

  public @NonNull LiveData<List<Item>> getList() {
    return pagedData.getData();
  }

  public @NonNull PagingController<String> getPagingController() {
    return pagedData.getController();
  }

  public void prependItems() {
    String key = dataSource.prepend();
    pagedData.getController().onDataItemInserted(key, 0);
  }

}
