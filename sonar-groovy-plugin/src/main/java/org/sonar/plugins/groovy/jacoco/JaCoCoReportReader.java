/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010-2020 SonarSource SA & Community
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
package org.sonar.plugins.groovy.jacoco;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.ISessionInfoVisitor;

public class JaCoCoReportReader {

  // Visible for testing
  static final String INCOMPATIBLE_JACOCO_ERROR =
      "You are using an incompatible JaCoCo binary format version, please consider upgrading to a supported JaCoCo version (0.8.x).";

  @Nullable private final File jacocoExecutionData;

  public JaCoCoReportReader(@Nullable File jacocoExecutionData) {
    this.jacocoExecutionData = jacocoExecutionData;
    verifyCurrentReportFormat(jacocoExecutionData);
  }

  /**
   * Read JaCoCo report determining the format to be used.
   *
   * @param executionDataVisitor visitor to store execution data.
   * @param sessionInfoStore visitor to store info session.
   * @return true if binary format is the latest one.
   * @throws IOException in case of error or binary format not supported.
   */
  public JaCoCoReportReader readJacocoReport(
      IExecutionDataVisitor executionDataVisitor, ISessionInfoVisitor sessionInfoStore) {
    if (jacocoExecutionData == null) {
      return this;
    }

    JaCoCoExtensions.logger().info("Analysing {}", jacocoExecutionData);
    try (InputStream inputStream =
        new BufferedInputStream(new FileInputStream(jacocoExecutionData))) {
      ExecutionDataReader reader = new ExecutionDataReader(inputStream);
      reader.setSessionInfoVisitor(sessionInfoStore);
      reader.setExecutionDataVisitor(executionDataVisitor);
      reader.read();
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Unable to read %s", jacocoExecutionData.getAbsolutePath()), e);
    }
    return this;
  }

  private static void verifyCurrentReportFormat(@Nullable File jacocoExecutionData) {
    if (jacocoExecutionData == null) {
      return;
    }
    try (DataInputStream dis = new DataInputStream(new FileInputStream(jacocoExecutionData))) {
      byte firstByte = dis.readByte();
      if (firstByte != ExecutionDataWriter.BLOCK_HEADER
          || dis.readChar() != ExecutionDataWriter.MAGIC_NUMBER) {
        throw new IllegalStateException();
      }
      if (dis.readChar() != ExecutionDataWriter.FORMAT_VERSION) {
        throw new IllegalArgumentException(
            INCOMPATIBLE_JACOCO_ERROR);
      }
    } catch (IOException | IllegalStateException e) {
      throw new IllegalArgumentException(
          String.format(
              "Unable to read %s to determine JaCoCo binary format.",
              jacocoExecutionData.getAbsolutePath()),
          e);
    }
  }

  /** Caller must guarantee that {@code classFiles} are actually class file. */
  public CoverageBuilder analyzeFiles(
      ExecutionDataStore executionDataStore, Collection<File> classFiles) {
    CoverageBuilder coverageBuilder = new CoverageBuilder();
    Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
    for (File classFile : classFiles) {
      analyzeClassFile(analyzer, classFile);
    }
    return coverageBuilder;
  }

  /** Caller must guarantee that {@code classFile} is actually class file. */
  private static void analyzeClassFile(Analyzer analyzer, File classFile) {
    try (InputStream inputStream = new FileInputStream(classFile)) {
      analyzer.analyzeClass(inputStream, classFile.getPath());
    } catch (IOException e) {
      // (Godin): in fact JaCoCo includes name into exception
      JaCoCoExtensions.logger()
          .warn("Exception during analysis of file " + classFile.getAbsolutePath(), e);
    }
  }
}
