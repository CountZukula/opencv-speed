import io.vertx.reactivex.core.Vertx
import javafx.application.Application.launch

// publicly available single Vertx instance, have fun
val vertx:Vertx = Vertx.vertx()

fun main() {
    // launch the JavaFX application
    launch(FxApplication::class.java)
}
