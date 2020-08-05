import javafx.application.Application
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.stage.WindowEvent

class FxApplication : Application() {


    override fun start(primaryStage: Stage) {
        try {
            // load the FXML resource
            val loader = FXMLLoader(javaClass.getResource("application.fxml"))
            // store the root element so that the controllers can use it
            val rootElement = loader.load<Any>() as BorderPane
            // create and style a scene
            val scene = Scene(rootElement, 800.0, 600.0)
            scene.stylesheets.add(javaClass.getResource("application.css").toExternalForm())
            // create the stage with the given title and the previously created
            // scene
            primaryStage.title = "JavaFX meets OpenCV"
            primaryStage.scene = scene
            // show the GUI
            primaryStage.show()
            // set the proper behavior on closing the application
            val controller: ControllerSequential = loader.getController()
            primaryStage.onCloseRequest = EventHandler<WindowEvent?> {
                controller.setClosed()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
