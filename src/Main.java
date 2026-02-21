import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main extends Canvas implements Runnable, KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    private boolean running;
    private Thread loop;
    private boolean wireframe;

    private float camX=0, camY=0, camZ=-5;
    private float yaw=0, pitch=0;

    private float moveSpeed=4f;
    private final float near=0.1f;

    private final Set<Integer> keys=new HashSet<>();
    private int lastMouseX,lastMouseY;
    private boolean dragging=false;

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> {
            JFrame frame=new JFrame("3D Engine");
            Main canvas=new Main();
            canvas.setPreferredSize(new Dimension(800,600));

            canvas.addKeyListener(canvas);
            canvas.addMouseMotionListener(canvas);
            canvas.addMouseListener(canvas);
            canvas.addMouseWheelListener(canvas);
            canvas.setFocusable(true);

            JPanel controls=new JPanel();
            controls.setBackground(new Color(30,30,40));
            JButton toggle=new JButton("Toggle Wireframe");
            toggle.addActionListener(e->canvas.wireframe=!canvas.wireframe);
            controls.add(toggle);

            frame.setLayout(new BorderLayout());
            frame.add(controls,BorderLayout.NORTH);
            frame.add(canvas,BorderLayout.CENTER);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            canvas.requestFocus();
            canvas.start();
        });
    }

    private void start(){ running=true; loop=new Thread(this); loop.start(); }

    @Override
    public void run(){
        createBufferStrategy(3);
        BufferStrategy bs=getBufferStrategy();

        long last=System.nanoTime();

        while(running){

            long now=System.nanoTime();
            float dt=(now-last)/1_000_000_000f;
            last=now;

            updateCamera(dt);

            Graphics2D g=(Graphics2D)bs.getDrawGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0,0,getWidth(),getHeight());

            drawCube(g);

            g.dispose();
            bs.show();

            try{Thread.sleep(2);}catch(Exception ignored){}
        }
    }

    private void updateCamera(float dt){

        float speed=moveSpeed*dt;
        if(keys.contains(KeyEvent.VK_SHIFT)) speed*=3f;

        float fx=(float)Math.sin(yaw);
        float fz=(float)Math.cos(yaw);

        float rx=(float)Math.sin(yaw+Math.PI/2);
        float rz=(float)Math.cos(yaw+Math.PI/2);

        if(keys.contains(KeyEvent.VK_W)){ camX+=fx*speed; camZ+=fz*speed; }
        if(keys.contains(KeyEvent.VK_S)){ camX-=fx*speed; camZ-=fz*speed; }
        if(keys.contains(KeyEvent.VK_A)){ camX-=rx*speed; camZ-=rz*speed; }
        if(keys.contains(KeyEvent.VK_D)){ camX+=rx*speed; camZ+=rz*speed; }
        if(keys.contains(KeyEvent.VK_Q)) camY-=speed;
        if(keys.contains(KeyEvent.VK_E)) camY+=speed;
    }

    private void drawCube(Graphics2D g){

        Vec3[] v={
                new Vec3(-1,-1,-1),new Vec3(1,-1,-1),
                new Vec3(1,1,-1),new Vec3(-1,1,-1),
                new Vec3(-1,-1,1),new Vec3(1,-1,1),
                new Vec3(1,1,1),new Vec3(-1,1,1)
        };

        int[][] f={
                {0,1,2},{0,2,3},{4,5,6},{4,6,7},
                {0,1,5},{0,5,4},{2,3,7},{2,7,6},
                {1,2,6},{1,6,5},{0,3,7},{0,7,4}
        };

        List<Triangle> tris=new ArrayList<>();

        for(int[] face:f){

            Vec3 a=camera(v[face[0]]);
            Vec3 b=camera(v[face[1]]);
            Vec3 c=camera(v[face[2]]);

            if(a.z<=near && b.z<=near && c.z<=near) continue;

            tris.add(new Triangle(projectSafe(a),projectSafe(b),projectSafe(c)));
        }

        tris.sort(Comparator.comparingDouble((Triangle t)->-t.depth()));

        for(Triangle t:tris){

            int[] xs={(int)t.a.x,(int)t.b.x,(int)t.c.x};
            int[] ys={(int)t.a.y,(int)t.b.y,(int)t.c.y};

            if(!wireframe){
                g.setColor(Color.WHITE);
                g.fillPolygon(xs,ys,3);
            }

            g.setColor(new Color(15,25,130));
            g.drawPolygon(xs,ys,3);
        }
    }

    private Vec3 projectSafe(Vec3 p){
        if(p.z<near) p.z=near;
        float scale=220f/(p.z+3f);
        return new Vec3(
                p.x*scale+getWidth()/2f,
                -p.y*scale+getHeight()/2f,
                p.z
        );
    }

    private Vec3 camera(Vec3 p){

        float x=p.x-camX, y=p.y-camY, z=p.z-camZ;

        float cosy=(float)Math.cos(-yaw), siny=(float)Math.sin(-yaw);
        float cosx=(float)Math.cos(-pitch), sinx=(float)Math.sin(-pitch);

        float dx=x*cosy - z*siny;
        float dz=x*siny + z*cosy;

        float dy=y*cosx - dz*sinx;
        dz=y*sinx + dz*cosx;

        return new Vec3(dx,dy,dz);
    }

    static class Vec3{
        float x,y,z;
        Vec3(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }

    static class Triangle{
        Vec3 a,b,c;
        Triangle(Vec3 a,Vec3 b,Vec3 c){this.a=a;this.b=b;this.c=c;}
        float depth(){return(a.z+b.z+c.z)/3f;}
    }

    public void keyPressed(KeyEvent e){ keys.add(e.getKeyCode()); }
    public void keyReleased(KeyEvent e){ keys.remove(e.getKeyCode()); }
    public void keyTyped(KeyEvent e){}

    public void mousePressed(MouseEvent e){ dragging=true; lastMouseX=e.getX(); lastMouseY=e.getY(); }
    public void mouseReleased(MouseEvent e){ dragging=false; }

    public void mouseDragged(MouseEvent e){
        if(!dragging) return;
        int dx=e.getX()-lastMouseX;
        int dy=e.getY()-lastMouseY;
        lastMouseX=e.getX();
        lastMouseY=e.getY();

        yaw+=dx*0.006f;
        pitch+=dy*0.006f;

        float limit=(float)Math.toRadians(89);
        if(pitch>limit) pitch=limit;
        if(pitch<-limit) pitch=-limit;
    }

    public void mouseWheelMoved(MouseWheelEvent e){
        moveSpeed+=e.getWheelRotation()*-0.5f;
        if(moveSpeed<0.5f) moveSpeed=0.5f;
    }

    public void mouseMoved(MouseEvent e){}
    public void mouseClicked(MouseEvent e){}
    public void mouseEntered(MouseEvent e){}
    public void mouseExited(MouseEvent e){}
}
