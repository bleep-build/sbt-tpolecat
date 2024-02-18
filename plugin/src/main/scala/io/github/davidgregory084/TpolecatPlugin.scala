/*
 * Copyright 2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.sbt.tpolecat

import bleep.model
import bleep.nosbt.librarymanagement

import scala.util.Try
import org.typelevel.scalacoptions._

class TpolecatPlugin(
    // The default mode to use for configuring scalac options via the sbt-tpolecat plugin.
    val tpolecatDefaultOptionsMode: OptionsMode = CiMode,
    // The set of scalac options that will be excluded.
    val tpolecatExcludeOptions: Set[ScalacOption] = Set.empty
) {
  // The environment variable to use to enable the sbt-tpolecat verbose mode.
  val tpolecatVerboseModeEnvVar: String = "SBT_TPOLECAT_VERBOSE"
  // The environment variable to use to enable the sbt-tpolecat development mode.
  val tpolecatDevModeEnvVar: String = "SBT_TPOLECAT_DEV"
  // The environment variable to use to enable the sbt-tpolecat continuous integration mode.
  val tpolecatCiModeEnvVar: String = "SBT_TPOLECAT_CI"
  // The environment variable to use to enable the sbt-tpolecat release mode.
  val tpolecatReleaseModeEnvVar: String = "SBT_TPOLECAT_RELEASE"

  // The mode to use for configuring scalac options via the sbt-tpolecat plugin.
  def tpolecatOptionsMode: OptionsMode =
    if (sys.env.contains(tpolecatReleaseModeEnvVar)) ReleaseMode
    else if (sys.env.contains(tpolecatCiModeEnvVar)) CiMode
    else if (sys.env.contains(tpolecatDevModeEnvVar)) DevMode
    else if (sys.env.contains(tpolecatVerboseModeEnvVar)) VerboseMode
    else tpolecatDefaultOptionsMode

  // The set of scalac options that will be applied by the sbt-tpolecat plugin in the development mode.
  def tpolecatDevModeOptions: Set[ScalacOption] =
    ScalacOptions.default

  // The set of scalac options that will be applied by the sbt-tpolecat plugin in the continuous integration mode.
  def tpolecatCiModeOptions: Set[ScalacOption] =
    tpolecatDevModeOptions + ScalacOptions.fatalWarnings

  def tpolecatVerboseModeOptions: Set[ScalacOption] =
    tpolecatDevModeOptions ++ ScalacOptions.verboseOptions

  // The set of scalac options that will be applied by the sbt-tpolecat plugin in the release mode.
  def tpolecatReleaseModeOptions: Set[ScalacOption] =
    tpolecatCiModeOptions + ScalacOptions.optimizerMethodLocal

  // The set of scalac options that will be applied by the sbt-tpolecat plugin.
  def tpolecatScalacOptions: Set[ScalacOption] =
    tpolecatOptionsMode match {
      case VerboseMode => tpolecatVerboseModeOptions
      case DevMode     => tpolecatDevModeOptions
      case CiMode      => tpolecatCiModeOptions
      case ReleaseMode => tpolecatReleaseModeOptions
    }

  private[TpolecatPlugin] def supportedOptionsFor(
      version: String,
      modeScalacOptions: Set[ScalacOption]
  ): Set[ScalacOption] =
    (librarymanagement.CrossVersion.partialVersion(version), version.split('.')) match {
      case (Some((maj, min)), Array(maj2, min2, patch)) if maj.toString == maj2 && min.toString == min2 =>
        val patchVersion = patch.takeWhile(_.isDigit)
        val binaryVersion = ScalaVersion(maj, min, Try(patchVersion.toLong).getOrElse(0))
        ScalacOptions.optionsForVersion(binaryVersion, modeScalacOptions)
      case (Some((maj, min)), _) =>
        val binaryVersion = ScalaVersion(maj, min, 0)
        ScalacOptions.optionsForVersion(binaryVersion, modeScalacOptions)
      case (None, _) =>
        Set.empty[ScalacOption]
    }

  def scalacOptions(scalaVersion: String): model.Options = {
    val pluginOptions = tpolecatScalacOptions
    val pluginExcludes = tpolecatExcludeOptions
    val selectedOptions = pluginOptions.diff(pluginExcludes)
    model.Options {
      supportedOptionsFor(scalaVersion, selectedOptions).map {
        case x if x.args.isEmpty => model.Options.Opt.Flag(x.option)
        case x                   => model.Options.Opt.WithArgs(x.option, x.args)
      }
    }
  }

}
