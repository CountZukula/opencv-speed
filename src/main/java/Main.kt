import javafx.application.Application
import javafx.application.Application.launch
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage


fun main() {
    launch(Main::class.java)
}

class Main : Application() {

    override fun start(stage: Stage) {

        val root = FXMLLoader.load<Parent>(javaClass.getResource("fxml_example.fxml"))
        val scene = Scene(root, 300.0, 275.0)

        stage.setTitle("FXML Welcome")
        stage.setScene(scene)
        stage.show()
    }

}

