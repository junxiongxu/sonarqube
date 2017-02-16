/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.component.ws;

import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentService;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_NEW_PROJECT;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_PROJECT;

public class UpdateKeyActionTest {
  private static final String ANOTHER_KEY = "another_key";
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  ComponentDbTester componentDb = new ComponentDbTester(db);
  DbClient dbClient = db.getDbClient();

  ComponentService componentService = mock(ComponentService.class);
  org.sonar.server.project.ws.UpdateKeyAction action = spy(new org.sonar.server.project.ws.UpdateKeyAction(dbClient, new ComponentFinder(dbClient), componentService));

  WsActionTester ws = new WsActionTester(new UpdateKeyAction(action));

  @Test
  public void call() throws Exception {
    ComponentDto project = insertProject();

    callByKey(project.key(), ANOTHER_KEY);

    verify(action).handle(any(Request.class), any(Response.class));
    assertCallComponentService(ANOTHER_KEY);
  }

  @Test
  public void api_definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.since()).isEqualTo("6.1");
    assertThat(definition.isPost()).isTrue();
    assertThat(definition.key()).isEqualTo("update_key");
    assertThat(definition.params())
      .hasSize(3)
      .extracting(Param::key)
      .containsOnlyOnce("projectId", "project", "newProject");

    assertThat(definition.description()).isEqualTo("See api/projects/update_key");
    assertThat(definition.deprecatedSince()).isEqualTo("6.4");
  }

  private void assertCallComponentService(@Nullable String newKey) {
    verify(componentService).updateKey(any(DbSession.class), any(ComponentDto.class), eq(newKey));
  }

  private ComponentDto insertProject() {
    return componentDb.insertComponent(newProjectDto(db.organizations().insert()));
  }

  private String callByKey(@Nullable String key, @Nullable String newKey) {
    return call(key, newKey);
  }

  private String call(@Nullable String key, @Nullable String newKey) {
    TestRequest request = ws.newRequest();

    if (key != null) {
      request.setParam(PARAM_PROJECT, key);
    }
    if (newKey != null) {
      request.setParam(PARAM_NEW_PROJECT, newKey);
    }

    return request.execute().getInput();
  }
}
