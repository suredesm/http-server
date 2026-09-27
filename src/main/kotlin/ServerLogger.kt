package lk.fincore

import java.awt.Color
import java.awt.Font
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.io.OutputStream
import java.io.PrintStream
import javax.swing.JFrame
import javax.swing.JTextArea

class ServerLogger(title: String): JFrame(title) {

    companion object {
        private var count = 0

        fun setupAndStartDefault(title: String = "Server Logger"): ServerLogger {
            if (count++ > 10) throw RuntimeException("Too many ServerLoggers!")
            return ServerLogger(title).also {
                it.setSize(600, 338)
                it.contentPane.background = Color.BLACK
                it.defaultCloseOperation = DISPOSE_ON_CLOSE
                it.isVisible = true
            }
        }
    }

    private val textArea = JTextArea().also { area ->
        area.isEditable = false
        area.background = Color.BLACK
        area.foreground = Color.WHITE
        area.caretColor = Color.WHITE
        area.font = Font(Font.MONOSPACED, Font.PLAIN, 13)
    }

    private val outputStream = object : OutputStream() {
        private var isLastCarriage = false
        override fun write(b: Int) {
            val char = b.toChar()
            if (char == '\r') {
                isLastCarriage = true
            } else if (isLastCarriage && char != '\n') {
                val last = textArea.text.lastIndexOf('\n') + 1
                val text = textArea.text.take(last) + char
                textArea.text = text
                isLastCarriage = false
            } else {
                textArea.append(char.toString())
                isLastCarriage = false
            }
        }

    }
    private val printStream = PrintStream(outputStream, true)
    private var windowClosedListener: (() -> Unit)? = null

    init {
        add(textArea)
        addWindowListener(object : WindowListener {
            override fun windowOpened(e: WindowEvent?) {}

            override fun windowClosing(e: WindowEvent?) {
                windowClosedListener?.invoke()
                count--
            }

            override fun windowClosed(e: WindowEvent?) {}

            override fun windowIconified(e: WindowEvent?) {}

            override fun windowDeiconified(e: WindowEvent?) {}

            override fun windowActivated(e: WindowEvent?) {}

            override fun windowDeactivated(e: WindowEvent?) {}
        })
    }

    fun print(text: String) = printStream.print(text)

    fun println() = printStream.println()

    fun println(text: String) = printStream.println(text)

    fun onWindowClosed(callback: (() -> Unit)?) {
        windowClosedListener = callback
    }
}