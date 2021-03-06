/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.impl.persistence.entity;

import java.util.List;
import java.util.Map;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.impl.Page;
import org.activiti.engine.impl.TaskQueryImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.AbstractManager;
import org.activiti.engine.task.Task;


/**
 * @author Tom Baeyens
 */
public class TaskManager extends AbstractManager {

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void deleteTasksByProcessInstanceId(String processInstanceId, String deleteReason, boolean cascade) {
    List<TaskEntity> tasks = (List) getDbSqlSession()
      .createTaskQuery()
      .processInstanceId(processInstanceId)
      .list();
  
    String reason = (deleteReason == null || deleteReason.length() == 0) ? TaskEntity.DELETE_REASON_DELETED : deleteReason;
    
    for (TaskEntity task: tasks) {
      deleteTask(task, reason, cascade);
    }
  }

  public void deleteTask(TaskEntity task, String deleteReason, boolean cascade) {
    if (!task.isDeleted()) {
      task.setDeleted(true);
      
      CommandContext commandContext = Context.getCommandContext();
      String taskId = task.getId();
      
      List<Task> subTasks = findTasksByParentTaskId(taskId);
      for (Task subTask: subTasks) {
        deleteTask((TaskEntity) subTask, deleteReason, cascade);
      }
      
      commandContext
        .getIdentityLinkManager()
        .deleteIdentityLinksByTaskId(taskId);

      commandContext
        .getVariableInstanceManager()
        .deleteVariableInstanceByTask(task);

      if (cascade) {
        commandContext
          .getHistoricTaskInstanceManager()
          .deleteHistoricTaskInstanceById(taskId);
      } else {
        commandContext
          .getHistoryManager()
          .recordTaskEnd(taskId, deleteReason);
      }
        
      getDbSqlSession().delete(task);
    }
  }


  public TaskEntity findTaskById(String id) {
    if (id == null) {
      throw new ActivitiException("Invalid task id : null");
    }
    return (TaskEntity) getDbSqlSession().selectOne("selectTask", id);
  }

  @SuppressWarnings("unchecked")
  public List<TaskEntity> findTasksByExecutionId(String executionId) {
    return getDbSqlSession().selectList("selectTasksByExecutionId", executionId);
  }
  
  @SuppressWarnings("unchecked")
  public List<TaskEntity> findTasksByProcessInstanceId(String processInstanceId) {
    return getDbSqlSession().selectList("selectTasksByProcessInstanceId", processInstanceId);
  }
  
  @Deprecated
  public List<Task> findTasksByQueryCriteria(TaskQueryImpl taskQuery, Page page) {
    taskQuery.setFirstResult(page.getFirstResult());
    taskQuery.setMaxResults(page.getMaxResults());
    return findTasksByQueryCriteria(taskQuery);
  }
  
  @SuppressWarnings("unchecked")
  public List<Task> findTasksByQueryCriteria(TaskQueryImpl taskQuery) {
    final String query = "selectTaskByQueryCriteria";
    return getDbSqlSession().selectList(query, taskQuery);
  }

  public long findTaskCountByQueryCriteria(TaskQueryImpl taskQuery) {
    return (Long) getDbSqlSession().selectOne("selectTaskCountByQueryCriteria", taskQuery);
  }
  
  @SuppressWarnings("unchecked")
  public List<Task> findTasksByNativeQuery(Map<String, Object> parameterMap, int firstResult, int maxResults) {
    return getDbSqlSession().selectListWithRawParameter("selectTaskByNativeQuery", parameterMap, firstResult, maxResults);
  }

  public long findTaskCountByNativeQuery(Map<String, Object> parameterMap) {
    return (Long) getDbSqlSession().selectOne("selectTaskCountByNativeQuery", parameterMap);
  }

  @SuppressWarnings("unchecked")
  public List<Task> findTasksByParentTaskId(String parentTaskId) {
    return getDbSqlSession().selectList("selectTasksByParentTaskId", parentTaskId);
  }

  public void deleteTask(String taskId, String deleteReason, boolean cascade) {
    TaskEntity task = Context
      .getCommandContext()
      .getTaskManager()
      .findTaskById(taskId);
    
    if (task!=null) {
      if(task.getExecutionId() != null) {
        throw new ActivitiException("The task cannot be deleted because is part of a running process");
      }
      
      String reason = (deleteReason == null || deleteReason.length() == 0) ? TaskEntity.DELETE_REASON_DELETED : deleteReason;
      deleteTask(task, reason, cascade);
    } else if (cascade) {
      Context
        .getCommandContext()
        .getHistoricTaskInstanceManager()
        .deleteHistoricTaskInstanceById(taskId);
    }
  }
}
