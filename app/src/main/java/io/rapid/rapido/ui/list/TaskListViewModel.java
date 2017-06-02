package io.rapid.rapido.ui.list;


import android.databinding.Observable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;

import java.util.Date;
import java.util.Set;

import io.rapid.ConnectionState;
import io.rapid.Rapid;
import io.rapid.RapidCollectionReference;
import io.rapid.RapidCollectionSubscription;
import io.rapid.RapidDocumentReference;
import io.rapid.Sorting;
import io.rapid.rapido.Config;
import io.rapid.rapido.model.Task;
import io.rapid.rapido.ui.filter.FilterState;
import io.rapid.rapido.ui.filter.FilterViewModel;
import io.rapid.rapido.ui.list.item.TaskItemHandler;
import io.rapid.rapido.ui.list.item.TaskItemViewModel;
import io.rapid.rapido.ui.list.item.TaskListAdapter;
import io.rapid.rapido.util.DemoUtils;


/**
 * ViewModel for TaskList screen
 */
public class TaskListViewModel implements TaskItemHandler, FilterViewModel.OnFilterChangedListener {
	public final ObservableField<String> searchQuery = new ObservableField<>();
	public ObservableBoolean searching = new ObservableBoolean();
	public ObservableField<ConnectionState> connectionState = new ObservableField<>();
	public TaskListAdapter taskListAdapter = new TaskListAdapter();

	private Sorting mOrderSorting;
	private String mOrderProperty;
	private FilterState mFilterState;
	private Set<String> mFilterTags;
	private RapidCollectionSubscription mSubscription;
	private TaskListView mView;


	@Override
	public void deleteTask(String id) {
		// delete task from collection and handle error properly
		getTasksReference().document(id).delete()
				.onError(error -> mView.showToast(error.getMessage()));
	}


	@Override
	public void onTaskUpdated(String id, Task task) {
		RapidDocumentReference<Task> doc;
		if(id != null) {
			// we are updating existing document
			doc = getTasksReference().document(id);
		} else {
			// we are creating new document
			doc = getTasksReference().newDocument();
			task.setCreatedAt(new Date());
		}

		// perform data mutation and handle error properly
		doc.mutate(task).onError(error -> mView.showToast(error.getMessage()));
	}


	@Override
	public void editTask(String id, Task task) {
		mView.showEditDialog(id, task);
	}


	@Override
	public void onFilterChanged(String orderProperty, Sorting sorting, FilterState filterState, Set<String> filterTags) {
		mOrderProperty = orderProperty;
		mOrderSorting = sorting;
		mFilterState = filterState;
		mFilterTags = filterTags;

		// filter has changed unsubscribe and resubscribe with new params
		if(mSubscription != null) {
			unsubscribe();
			subscribe();
		}
	}


	public void initialize(TaskListView view) {
		mView = view;

		// add connection state listener to indicate current state in the UI
		Rapid.getInstance().addConnectionStateListener(connectionState::set);

		searchQuery.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
			@Override
			public void onPropertyChanged(Observable sender, int propertyId) {
				if(mSubscription != null) {
					unsubscribe();
					subscribe();
				}
			}
		});
	}


	public void onViewAttached() {
		// when view (Activity) is attached, we can subscribe
		// this will be called when activity goes into foreground
		subscribe();
	}


	public void onViewDetached() {
		// view (Activity) was detached - unsubscribe
		// this will be called when the Activity goes into background - we don't want to keep the connection to Rapid.io then
		Rapid.getInstance().removeAllConnectionStateListeners();
		unsubscribe();
	}


	public void deleteTaskAtPosition(int position) {
		TaskItemViewModel taskToDelete = taskListAdapter.getItems().get(position);
		deleteTask(taskToDelete.getId());
	}


	private RapidCollectionReference<Task> getTasksReference() {
		// get task collection reference and provide backing class for serialization/deserialization
		String collectionName = DemoUtils.checkCollectionName(Config.RAPID_TODO_COLLECTION_NAME);
		return Rapid.getInstance().collection(collectionName, Task.class);
	}


	private void unsubscribe() {
		mSubscription.unsubscribe();
	}


	private void subscribe() {
		RapidCollectionReference<Task> tasksReference = getTasksReference();

		// if search query is not empty - add it as a filter
		// we'll require that search query is part of Task's title or description
		String query = searchQuery.get();
		if(query != null && !query.isEmpty()) {
			tasksReference.beginOr()
					.contains("title", query)
					.contains("description", query)
					.endOr();
		}

		// if we want to display only specific tags - add it as a filter
		// let's use arrayContains() method which checks if array property contains desired String
		if(mFilterTags != null) {
			for(String tag : mFilterTags) {
				tasksReference.arrayContains("tags", tag);
			}
		}

		// if we want to display only tasks with specific state - add it as a filter
		if(mFilterState == FilterState.DONE)
			tasksReference.equalTo("done", true);
		else if(mFilterState == FilterState.NOT_DONE)
			tasksReference.equalTo("done", false);

		// finally, subscribe to the filtered collection
		mSubscription = tasksReference
				.orderBy(mOrderProperty, mOrderSorting) // order by specified property using specified sorting
				.map(document -> new TaskItemViewModel(document, this)) // wrap each RapidDocument by ItemViewModel so we can use it in our list
				.subscribe(items -> { // subscribe with list updates so we can properly animate our RecyclerView
					// update items in adapter
					taskListAdapter.setItems(items);
				})
				.onError(error -> {
					// handle errors properly
					error.printStackTrace();
					mView.showToast(error.getMessage());
				});
	}


}
