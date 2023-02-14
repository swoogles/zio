package zio.test.fileio

import scala.scalajs.js

object FileSandbox {
  trait FileJS {
    // Did copilot suggest this because that's the general recommendation,
    // or because I have scoverage open in another Intellij window?
    def write(path: String, data: String, mode: String = "a"): Unit
  }

  @js.native
  trait FS extends js.Object {
    def writeFileSync(
                       path: String,
                       data: String,
                       options: js.Dynamic = js.Dynamic.literal()
    ): Unit = js.native
  }

  private[test] trait NodeLikeFile extends FileJS {
    def require: js.Dynamic

    implicit lazy val fs: FS = require("fs").asInstanceOf[FS]

    def write(path: String, data: String, mode: String = "a") = {
      fs.writeFileSync(path, data, js.Dynamic.literal(flag = mode))
    }
  }

  private[test] object NodeFile extends NodeLikeFile {
    lazy val require = js.Dynamic.global.require
  }
}
