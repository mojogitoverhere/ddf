/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.ui.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * <b> This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library. </b> <br>
 * <br>
 * Creates {@link Task} objects and allows callers to report status to them. <br>
 * <br>
 * <b>Example Usage:</b><br>
 *
 * <pre>{@code
 * TaskMonitor blahMonitor = new TaskMonitor();
 * ..
 * Task task = blahMonitor.newTask();
 * asynchronouslyExecutingFunction(params, task);
 * // return task.getId() for callers to identify the task they are waiting for
 * ..
 * //inside asynchronouslyExecutingFunction(params, Task task):
 * long counter = 0;
 * task.started()
 * for (Individual item : workToDo) {
 *   doWorkOnThe(item);
 *   counter++;
 *   task.update(counter, workToDo.size());
 * }
 * task.finished()
 * task.putDetails(mapOfStringToObjectContainingDetails);
 * // task.failed() is available in case of task failure if it did not task.finished() correctly
 * }</pre>
 */
public class TaskMonitor {
  /* Id -> task */
  private Map<String, Task> tasks = new ConcurrentHashMap<>();

  /**
   * Creates a new Task
   *
   * @return The task to report status to
   */
  public Task newTask() {
    Task task = new Task();
    tasks.put(task.getId(), task);
    task.putDetails("created", Instant.now().toString());
    return task;
  }

  /**
   * Get a task by its id
   *
   * @param id The id associated with the task (the one returned by Task.getId())
   * @return The specified task or Null if not found
   */
  @Nullable
  public Task getTask(String id) {
    return tasks.get(id);
  }

  /**
   * Get all tasks
   *
   * @return List of known tasks
   */
  public List<Task> getTasks() {
    return new ArrayList<>(tasks.values());
  }

  /**
   * Delete a task by its id
   *
   * @param id The id associated with the task (the one returned by {@link Task#getId()}))
   */
  public void removeTask(String id) {
    tasks.remove(id);
  }

  public static class Task {
    private String id = UUID.randomUUID().toString();

    private volatile long total = 0;
    private volatile long current = 0;
    private volatile Instant started = null;
    private volatile boolean finished = false;
    private volatile boolean failed = false;
    private volatile Map<String, Object> details = new ConcurrentHashMap<>();

    private Task() {}

    /**
     * Update the tasks' progress.
     *
     * @param current How many items have already been completed
     * @param total The total number of items being worked on
     */
    public void update(long current, long total) {
      this.current = current;
      this.total = total;
    }

    /** Sets the task as finished */
    public void finished() {
      finished = true;
    }

    /** Marks the task as failed and finished. */
    public void failed() {
      finished();
      failed = true;
    }

    /** Marks the task as started by setting {@link Task#started()} */
    public void started() {
      started = Instant.now();
    }

    /**
     * Add a key and value to the details map
     *
     * @param key key of a detail to add
     * @param value value of a detail to add
     * @return the object that was replaced or null if nothing was replaced
     */
    public Object putDetails(String key, Object value) {
      return details.put(key, value);
    }

    /**
     * Add a map of values to the details map
     *
     * @param details map of details to add
     */
    public void putDetails(Map<String, Object> details) {
      this.details.putAll(details);
    }

    /**
     * Returns the time that {@link Task#started()} was called
     *
     * @return the Instant that it was started or null
     */
    public Instant getStarted() {
      return started;
    }

    /** @return The tasks id */
    public String getId() {
      return id;
    }

    /** @return The total number of items being worked on */
    public long getTotal() {
      return total;
    }

    /** @return The current number of completed items */
    public long getCurrent() {
      return current;
    }

    /** @return Boolean if the task has finished and is no longer doing work */
    public boolean isFinished() {
      return finished;
    }

    /** @return Boolean if the task has failed */
    public boolean isFailed() {
      return failed;
    }

    /**
     * Callers should treat this map as immutable and should call {@link Task#putDetails(Map)} if
     * they wish to update it.
     *
     * @return The map of details currently associated with this task
     */
    public Map<String, Object> getDetails() {
      return details;
    }
  }
}
