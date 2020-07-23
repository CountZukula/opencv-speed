import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import java.net.URL
import java.util.*

class Controller {

    @FXML // The reference of inputText will be injected by the FXML loader
    private val inputText: TextField? = null

    // The reference of outputText will be injected by the FXML loader
    @FXML
    private val outputText: TextArea? = null

    // location and resources will be automatically injected by the FXML loader
    @FXML
    private val location: URL? = null

    @FXML
    private val resources: ResourceBundle? = null

    @FXML
    private fun initialize() {
    }

    @FXML
    private fun printOutput() {
        outputText?.text = inputText?.text
    }

    fun clickButton(actionEvent: ActionEvent) {
        println("THIS GOT CLICKED")
    }
}