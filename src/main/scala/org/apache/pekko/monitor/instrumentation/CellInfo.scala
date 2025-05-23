/*
 * =========================================================================================
 * Copyright © 2017,2018 Workday, Inc.
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package org.apache.pekko.monitor.instrumentation

import com.github.pjfanning.micrometer.pekko.{Entity, MetricsConfig}
import org.apache.pekko.actor.{ActorRef, ActorSystem, Cell}
import org.apache.pekko.routing.{NoRouter, RoutedActorRef}

case class CellInfo(entity: Entity, actorSystemName: String, isRouter: Boolean, isRoutee: Boolean, isTracked: Boolean,
    trackingGroups: List[String], actorCellCreation: Boolean)

object CellInfo {

  // use String instead of Class[_] to avoid requiring a runtime dependency on the pekko-actor-typed jar
  val TypedActorAdapterClassName = "org.apache.pekko.actor.typed.internal.adapter.ActorAdapter"

  def cellName(system: ActorSystem, ref: ActorRef): String =
    s"""${system.name}/${ref.path.elements.mkString("/")}"""

  def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): CellInfo = {
    def hasRouterProps(cell: Cell): Boolean = cell.props.deploy.routerConfig != NoRouter

    val pathString = ref.path.elements.mkString("/")
    val isTyped = cell.props.actorClass().getName == TypedActorAdapterClassName
    val isRootSupervisor = if (isTyped)
      pathString != "user"
    else
      pathString.isEmpty || pathString == "user" || pathString == "system"
    val isRouter = hasRouterProps(cell)
    val isRoutee = parent.isInstanceOf[RoutedActorRef]

    val name = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val category = if (isRouter || isRoutee) MetricsConfig.Router else MetricsConfig.Actor
    val entity = Entity(name, category)
    val isTracked = !isRootSupervisor && MetricsConfig.shouldTrack(category, name)
    val trackingGroups = if(isRoutee && isRootSupervisor) List() else MetricsConfig.actorShouldBeTrackedUnderGroups(name)

    CellInfo(entity, system.name, isRouter, isRoutee, isTracked, trackingGroups, actorCellCreation)
  }
}
