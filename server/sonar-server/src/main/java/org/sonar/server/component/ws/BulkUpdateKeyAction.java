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

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;

public class BulkUpdateKeyAction implements ComponentsWsAction {
  private final org.sonar.server.project.ws.BulkUpdateKeyAction action;

  public BulkUpdateKeyAction(org.sonar.server.project.ws.BulkUpdateKeyAction projectsAction) {
    this.action = projectsAction;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction projectsAction = action.doDefine(context);

    projectsAction.setDescription("See api/projects/bulk_update_key")
      .setDeprecatedSince("6.4");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    action.handle(request, response);
  }
}
