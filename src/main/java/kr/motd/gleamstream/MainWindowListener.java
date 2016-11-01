package kr.motd.gleamstream;

public interface MainWindowListener {
    void onKey(int key, int scancode, int action, int mods);
    void onCursorPos(double xpos, double ypos);
    void onMouseButton(int button, int action, int mods);
    void onScroll(double xoffset, double yoffset);
    void onJoystick(int jid, int event);
}
