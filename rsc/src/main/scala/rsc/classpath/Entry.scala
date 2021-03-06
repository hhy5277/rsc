// Copyright (c) 2017-2019 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (see LICENSE.md).
package rsc.classpath

import java.io._
import java.nio.file._
import java.util.jar._
import java.util.zip._

sealed trait Entry

case class PackageEntry() extends Entry

sealed trait FileEntry extends Entry {
  def openStream(): InputStream
}

case class UncompressedEntry(path: Path) extends FileEntry {
  def openStream(): InputStream = {
    val stream = Files.newInputStream(path)
    new BufferedInputStream(stream)
  }
}

case class CompressedEntry(jar: JarFile, entry: ZipEntry) extends FileEntry {
  def openStream(): InputStream = {
    jar.getInputStream(entry)
  }
}
