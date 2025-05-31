package com.lhstack.text

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.concurrentMapOf
import com.lhstack.tools.plugins.Helper
import com.lhstack.tools.plugins.IPlugin
import com.lhstack.tools.plugins.Logger
import org.apache.http.conn.util.InetAddressUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import javax.swing.*

class PluginImpl : IPlugin {

    companion object {
        var CACHE = concurrentMapOf<String, JComponent>()
        var LOGGER_CACHE = concurrentMapOf<String, Logger>()
    }

    override fun pluginIcon(): Icon = Helper.findIcon("port.svg", PluginImpl::class.java)

    override fun pluginTabIcon(): Icon = Helper.findIcon("port-tab.svg", PluginImpl::class.java)

    override fun closeProject(project: Project) {
        LOGGER_CACHE.remove(project.locationHash)
    }

    override fun closePanel(locationHash: String, pluginPanel: JComponent) {
        CACHE.remove(pluginPanel.name)
    }

    override fun createPanel(project: Project): JComponent {
        val id = UUID.randomUUID().toString()
        return CACHE.computeIfAbsent(id) {
            JPanel(BorderLayout()).apply {
                this.name = id
                val textArea = JBTextArea().apply {
                    this.font = Font("宋体", Font.PLAIN, 20)
                    this.border = JBUI.Borders.compound(
                        JBUI.Borders.empty(0, 4),
                        JBUI.Borders.customLine(JBColor.LIGHT_GRAY, 0, 1, 0, 1)
                    )
                }
                this.add(JPanel().apply {
                    this.layout = BoxLayout(this, BoxLayout.X_AXIS)
                    val input = JBTextField("127.0.0.1")
                    val timeout = JBIntSpinner(50, 0, 9000).apply {
                        this.toolTipText = "连接超时时间,单位毫秒"
                        this.preferredSize = Dimension(75, 0)
                    }
                    val startPort = JBIntSpinner(1, 1, 65535).apply {
                        this.toolTipText = "开始端口"
                        this.preferredSize = Dimension(80, 0)
                    }
                    val endPort = JBIntSpinner(65535, 1, 65535).apply {
                        this.toolTipText = "结束端口"
                        this.preferredSize = Dimension(80, 0)
                    }

                    this.add(input)
                    this.add(JLabel("连接超时: "))
                    this.add(timeout)
                    this.add(JLabel("端口范围: "))
                    this.add(startPort)
                    this.add(endPort)
                    this.add(JButton("检测").apply {
                        this.addActionListener {
                            if (input.text.isEmpty()) {
                                Notifications.Bus.notify(
                                    Notification(
                                        "", "警告", "请输入ip地址",
                                        NotificationType.WARNING
                                    )
                                )
                            } else {
                                if (InetAddressUtils.isIPv4Address(input.text) || InetAddressUtils.isIPv6Address(input.text)) {
                                    this.isEnabled = false
                                    this.text = "检测中..."
                                    textArea.text = ""
                                    checker(
                                        id,
                                        project,
                                        startPort.number,
                                        endPort.number,
                                        timeout.number,
                                        this,
                                        input.text,
                                        textArea
                                    )
                                } else {
                                    Notifications.Bus.notify(
                                        Notification(
                                            "", "警告", "请输入正确的的ip地址",
                                            NotificationType.WARNING
                                        )
                                    )
                                }
                            }

                        }
                    })
                }, BorderLayout.NORTH)
                this.add(JBScrollPane(textArea), BorderLayout.CENTER)
            }
        }
    }

    override fun supportMultiOpens(): Boolean {
        return true
    }

    private fun checker(
        id:String,
        project: Project,
        startPort: Int,
        endPort: Int,
        timeout: Int,
        button: JButton,
        host: String,
        textArea: JBTextArea
    ) {
        if (startPort > endPort) {
            Notifications.Bus.notify(
                Notification(
                    "", "警告", "开始端口不能大于结束端口",
                    NotificationType.WARNING
                )
            )
            SwingUtilities.invokeLater {
                textArea.text = textArea.text.trim()
                button.isEnabled = true
                button.text = "检测"
            }
            return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "检测中") {
            override fun run(indicator: ProgressIndicator) {
                for (port in startPort until endPort + 1) {
                    if (indicator.isCanceled || CACHE[id] == null) {
                        break
                    }
                    try {
                        indicator.text = "检测: $host:$port"
                        Socket().use {
                            it.connect(InetSocketAddress(host, port), timeout)
                            SwingUtilities.invokeLater {
                                textArea.append("$port up")
                                textArea.append("\n")
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
                SwingUtilities.invokeLater {
                    textArea.text = textArea.text.trim()
                    button.isEnabled = true
                    button.text = "检测"
                }
                Notifications.Bus.notify(
                    Notification(
                        "", "通知", "检测完毕",
                        NotificationType.INFORMATION
                    )
                )

            }

        })
    }

    override fun pluginName(): String = "端口扫描"

    override fun pluginDesc(): String = "端口扫描"

    override fun pluginVersion(): String = "0.0.2"
}
