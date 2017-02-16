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

import com.google.common.base.Throwables;
import java.io.IOException;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentService;
import org.sonar.server.component.index.ComponentIndexDefinition;
import org.sonar.server.es.EsTester;
import org.sonar.server.measure.index.ProjectMeasuresIndexDefinition;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.WsComponents.BulkUpdateKeyWsResponse;
import org.sonarqube.ws.WsComponents.BulkUpdateKeyWsResponse.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newModuleDto;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_DRY_RUN;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_FROM;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_PROJECT;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_PROJECT_ID;
import static org.sonarqube.ws.client.component.ComponentsWsParameters.PARAM_TO;

public class BulkUpdateKeyActionTest {
  private static final String MY_PROJECT_KEY = "my_project";
  private static final String FROM = "my_";
  private static final String TO = "your_";

  private System2 system2 = System2.INSTANCE;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public EsTester es = new EsTester(new ProjectMeasuresIndexDefinition(new MapSettings()),
    new ComponentIndexDefinition(new MapSettings()));
  @Rule
  public DbTester db = DbTester.create(system2);

  private ComponentDbTester componentDb = new ComponentDbTester(db);
  private DbClient dbClient = db.getDbClient();
  private ComponentFinder componentFinder = new ComponentFinder(dbClient);
  private ComponentService componentService = mock(ComponentService.class);
  private org.sonar.server.project.ws.BulkUpdateKeyAction action = spy(
    new org.sonar.server.project.ws.BulkUpdateKeyAction(dbClient, componentFinder, componentService, userSession));
  private WsActionTester ws = new WsActionTester(new BulkUpdateKeyAction(action));

  @Before
  public void setUp() {
    userSession.logIn().setRoot();
  }

  @Test
  public void bulk_update_project_key() throws Exception {
    ComponentDto project = insertMyProject();
    ComponentDto module = componentDb.insertComponent(newModuleDto(project).setKey("my_project:root:module"));
    componentDb.insertComponent(newModuleDto(project).setKey("my_project:root:inactive_module").setEnabled(false));
    componentDb.insertComponent(newFileDto(module, null).setKey("my_project:root:module:src/File.xoo"));
    componentDb.insertComponent(newFileDto(module, null).setKey("my_project:root:module:src/InactiveFile.xoo").setEnabled(false));

    BulkUpdateKeyWsResponse result = callByUuid(project.uuid(), FROM, TO);

    assertThat(result.getKeysCount()).isEqualTo(2);
    assertThat(result.getKeysList()).extracting(Key::getKey, Key::getNewKey, Key::getDuplicate)
      .containsExactly(
        tuple(project.key(), "your_project", false),
        tuple(module.key(), "your_project:root:module", false));
    verify(componentService).bulkUpdateKey(any(DbSession.class), eq(project.uuid()), eq(FROM), eq(TO));
    verify(action).handle(any(Request.class), any(Response.class));
  }

  @Test
  public void api_definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.isPost()).isTrue();
    assertThat(definition.since()).isEqualTo("6.1");
    assertThat(definition.key()).isEqualTo("bulk_update_key");
    assertThat(definition.params())
      .hasSize(5)
      .extracting(WebService.Param::key)
      .containsOnlyOnce("projectId", "project", "from", "to", "dryRun");

    assertThat(definition.description()).isEqualTo("See api/projects/bulk_update_key");
    assertThat(definition.deprecatedSince()).isEqualTo("6.4");
  }

  private ComponentDto insertMyProject() {
    return componentDb.insertComponent(newProjectDto(db.organizations().insert()).setKey(MY_PROJECT_KEY));
  }

  private WsComponents.BulkUpdateKeyWsResponse callByUuid(@Nullable String uuid, @Nullable String from, @Nullable String to) {
    return call(uuid, null, from, to, false);
  }

  private BulkUpdateKeyWsResponse call(@Nullable String uuid, @Nullable String key, @Nullable String from, @Nullable String to, @Nullable Boolean dryRun) {
    TestRequest request = ws.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);

    if (uuid != null) {
      request.setParam(PARAM_PROJECT_ID, uuid);
    }
    if (key != null) {
      request.setParam(PARAM_PROJECT, key);
    }
    if (from != null) {
      request.setParam(PARAM_FROM, from);
    }
    if (to != null) {
      request.setParam(PARAM_TO, to);
    }
    if (dryRun != null) {
      request.setParam(PARAM_DRY_RUN, String.valueOf(dryRun));
    }

    try {
      return WsComponents.BulkUpdateKeyWsResponse.parseFrom(request.execute().getInputStream());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
