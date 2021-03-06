
package pxb.android.axml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.googlecode.dex2jar.reader.io.DataOut;
import com.googlecode.dex2jar.reader.io.LeDataOut;

public class AxmlWriter extends AxmlVisitor {
    static class Attr {
        public StringItem name;
        public StringItem ns;
        public int resourceId;
        public int type;
        public Object value;

        public Attr(StringItem ns, StringItem name, int resourceId, int type, Object value) {
            super();
            this.ns = ns;
            this.name = name;
            this.resourceId = resourceId;
            this.type = type;
            this.value = value;
        }

        public void prepare(AxmlWriter axmlWriter) {
            ns = axmlWriter.updateNs(ns);
            if (this.name != null) {
                if (resourceId != -1) {
                    this.name = axmlWriter.updateWithResourceId(this.name, this.resourceId);
                } else {
                    this.name = axmlWriter.update(this.name);
                }
            }
            if (value instanceof StringItem) {
                value = axmlWriter.update((StringItem) value);
            }
        }

        @Override
        public int hashCode() {
            if (resourceId != 0 && resourceId != -1) {
                return resourceId;
            }
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((ns == null) ? 0 : ns.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Attr other = (Attr) obj;
            if (resourceId != other.resourceId)
                return false;
            if (resourceId != 0 && resourceId != -1) {
                return true;
            }
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (ns == null) {
                if (other.ns != null)
                    return false;
            } else if (!ns.equals(other.ns))
                return false;

            return true;
        }

    }

    static class NodeImpl extends NodeVisitor {
        private Set<Attr> attrs = new HashSet<Attr>();
        private List<NodeImpl> children = new ArrayList<NodeImpl>();
        private int line;
        private StringItem name;
        private StringItem ns;
        private StringItem text;
        private int textLineNumber;

        public NodeImpl(String ns, String name) {
            super(null);
            this.ns = ns == null ? null : new StringItem(ns);
            this.name = name == null ? null : new StringItem(name);
        }

        @Override
        public void attr(String ns, String name, int resourceId, int type, Object value) {
            if (name == null) {
                throw new RuntimeException("name can't be null");
            }
            Attr a = new Attr(ns == null ? null : new StringItem(ns), new StringItem(name), resourceId, type,
                    type == TYPE_STRING ? new StringItem((String) value) : value);
            attrs.add(a);
        }

        @Override
        public NodeVisitor child(String ns, String name) {
            NodeImpl child = new NodeImpl(ns, name);
            this.children.add(child);
            return child;
        }

        @Override
        public void end() {
        }

        @Override
        public void line(int ln) {
            this.line = ln;
        }

        public int prepare(AxmlWriter axmlWriter) {
            ns = axmlWriter.updateNs(ns);
            name = axmlWriter.update(name);

            for (Attr attr : this.sortedAttrs()) {
                attr.prepare(axmlWriter);
            }
            text = axmlWriter.update(text);
            int size = 24 + 36 + attrs.size() * 20;// 24 for end tag,36+x*20 for
                                                   // start tag
            for (NodeImpl child : children) {
                size += child.prepare(axmlWriter);
            }
            if (text != null) {
                size += 28;
            }
            return size;
        }

        List<Attr> sortedAttrs() {
            List<Attr> lAttrs = new ArrayList<Attr>(attrs);
            Collections.sort(lAttrs, new Comparator<Attr>() {

                @Override
                public int compare(Attr a, Attr b) {
                    if (a.ns == null) {
                        if (b.ns == null) {
                            return b.name.data.compareTo(a.name.data);
                        } else {
                            return 1;
                        }
                    } else if (b.ns == null) {
                        return -1;
                    } else {
                        int x = a.ns.data.compareTo(b.ns.data);
                        if (x == 0) {
                            x = a.resourceId - b.resourceId;
                            if (x == 0) {
                                return a.name.data.compareTo(b.name.data);
                            }
                        }
                        return x;
                    }
                }
            });
            return lAttrs;
        }

        @Override
        public void text(int ln, String value) {
            this.text = new StringItem(value);
            this.textLineNumber = ln;
        }

        void write(DataOut out) throws IOException {
            // start tag
            out.writeInt(AxmlReader.CHUNK_XML_START_TAG);
            out.writeInt(36 + attrs.size() * 20);
            out.writeInt(line);
            out.writeInt(0xFFFFFFFF);
            out.writeInt(ns != null ? this.ns.index : -1);
            out.writeInt(name.index);
            out.writeInt(0x00140014);// TODO
            out.writeShort(this.attrs.size());
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            for (Attr attr : this.sortedAttrs()) {
                out.writeInt(attr.ns == null ? -1 : attr.ns.index);
                out.writeInt(attr.name.index);
                out.writeInt(attr.value instanceof StringItem ? ((StringItem) attr.value).index : -1);
                out.writeInt((attr.type << 24) | 0x000008);
                Object v = attr.value;
                if (v instanceof StringItem) {
                    out.writeInt(((StringItem) attr.value).index);
                } else if (v instanceof Boolean) {
                    out.writeInt(Boolean.TRUE.equals(v) ? -1 : 0);
                } else {
                    out.writeInt((Integer) attr.value);
                }
            }

            if (this.text != null) {
                out.writeInt(AxmlReader.CHUNK_XML_TEXT);
                out.writeInt(28);
                out.writeInt(textLineNumber);
                out.writeInt(0xFFFFFFFF);
                out.writeInt(text.index);
                out.writeInt(0x00000008);
                out.writeInt(0x00000000);
            }

            // children
            for (NodeImpl child : children) {
                child.write(out);
            }

            // end tag
            out.writeInt(AxmlReader.CHUNK_XML_END_TAG);
            out.writeInt(24);
            out.writeInt(-1);
            out.writeInt(0xFFFFFFFF);
            out.writeInt(ns != null ? this.ns.index : -1);
            out.writeInt(name.index);
        }
    }

    static class Ns {
        int ln;
        StringItem prefix;
        StringItem uri;

        public Ns(StringItem prefix, StringItem uri, int ln) {
            super();
            this.prefix = prefix;
            this.uri = uri;
            this.ln = ln;
        }
    }

    private List<NodeImpl> firsts = new ArrayList<NodeImpl>(3);

    private Map<String, Ns> nses = new HashMap<String, Ns>();

    private List<StringItem> otherString = new ArrayList<StringItem>();

    private Map<String, StringItem> resourceId2Str = new HashMap<String, StringItem>();

    private List<Integer> resourceIds = new ArrayList<Integer>();

    private List<StringItem> resourceString = new ArrayList<StringItem>();

    private StringItems stringItems = new StringItems();

    // TODO add style support
    // private List<StringItem> styleItems = new ArrayList();

    @Override
    public void end() {
    }

    @Override
    public NodeVisitor first(String ns, String name) {
        NodeImpl first = new NodeImpl(ns, name);
        this.firsts.add(first);
        return first;
    }

    @Override
    public void ns(String prefix, String uri, int ln) {
        nses.put(uri, new Ns(prefix == null ? null : new StringItem(prefix), new StringItem(uri), ln));
    }

    private int prepare() throws IOException {
        int size = nses.size() * 24 * 2;
        for (NodeImpl first : firsts) {
            size += first.prepare(this);
        }
        {
            int a = 0;
            for (Map.Entry<String, Ns> e : nses.entrySet()) {
                Ns ns = e.getValue();
                if (ns == null) {
                    ns = new Ns(null, new StringItem(e.getKey()), 0);
                    e.setValue(ns);
                }
                if (ns.prefix == null) {
                    ns.prefix = new StringItem(String.format("axml_auto_%02d", a++));
                }
                ns.prefix = update(ns.prefix);
                ns.uri = update(ns.uri);
            }
        }
        this.stringItems.addAll(resourceString);
        resourceString = null;
        this.stringItems.addAll(otherString);
        otherString = null;
        this.stringItems.prepare();
        int stringSize = this.stringItems.getSize();
        if (stringSize % 4 != 0) {
            stringSize += 4 - stringSize % 4;
        }
        size += 8 + stringSize;
        size += 8 + resourceIds.size() * 4;
        return size;
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        DataOut out = new LeDataOut(os);
        int size = prepare();
        out.writeInt(AxmlReader.CHUNK_AXML_FILE);
        out.writeInt(size + 8);

        int stringSize = this.stringItems.getSize();
        int padding = 0;
        if (stringSize % 4 != 0) {
            padding = 4 - stringSize % 4;
        }
        out.writeInt(AxmlReader.CHUNK_STRINGS);
        out.writeInt(stringSize + padding + 8);
        this.stringItems.write(out);
        out.writeBytes(new byte[padding]);

        out.writeInt(AxmlReader.CHUNK_RESOURCEIDS);
        out.writeInt(8 + this.resourceIds.size() * 4);
        for (Integer i : resourceIds) {
            out.writeInt(i);
        }

        Stack<Ns> stack = new Stack<Ns>();
        for (Map.Entry<String, Ns> e : this.nses.entrySet()) {
            Ns ns = e.getValue();
            stack.push(ns);
            out.writeInt(AxmlReader.CHUNK_XML_START_NAMESPACE);
            out.writeInt(24);
            out.writeInt(-1);
            out.writeInt(0xFFFFFFFF);
            out.writeInt(ns.prefix.index);
            out.writeInt(ns.uri.index);
        }

        for (NodeImpl first : firsts) {
            first.write(out);
        }

        while (stack.size() > 0) {
            Ns ns = stack.pop();
            out.writeInt(AxmlReader.CHUNK_XML_END_NAMESPACE);
            out.writeInt(24);
            out.writeInt(ns.ln);
            out.writeInt(0xFFFFFFFF);
            out.writeInt(ns.prefix.index);
            out.writeInt(ns.uri.index);
        }
        return os.toByteArray();
    }

    StringItem update(StringItem item) {
        if (item == null)
            return null;
        int i = this.otherString.indexOf(item);
        if (i < 0) {
            StringItem copy = new StringItem(item.data);
            this.otherString.add(copy);
            return copy;
        } else {
            return this.otherString.get(i);
        }
    }

    StringItem updateNs(StringItem item) {
        if (item == null) {
            return null;
        }
        String ns = item.data;
        if (!this.nses.containsKey(ns)) {
            this.nses.put(ns, null);
        }
        return update(item);
    }

    StringItem updateWithResourceId(StringItem name, int resourceId) {
        String key = name.data + resourceId;
        StringItem item = this.resourceId2Str.get(key);
        if (item != null) {
            return item;
        } else {
            StringItem copy = new StringItem(name.data);
            resourceIds.add(resourceId);
            resourceString.add(copy);
            resourceId2Str.put(key, copy);
            return copy;
        }
    }
}
