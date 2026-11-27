package TaskManager.primer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * INSTRUCTOR PRIMER (~10 min) - List, Map, enum: everything the assignment needs, nothing more.
 * Run it live and change values as you go. Used in Steps 2, 6, 7.
 */
public class ListMapEnumPrimer {

    // ---------- enum: a fixed set of named values ----------
    enum Priority { LOW, MEDIUM, HIGH }

    // A tiny stand-in for Task so the sort/paging demo has something to work on.
    static class Item {
        final String title;
        final String dueDate; // YYYY-MM-DD or null
        Item(String title, String dueDate) {
            this.title = title;
            this.dueDate = dueDate;
        }
        String getTitle() { return title; }
        String getDueDate() { return dueDate; }
        @Override public String toString() { return title + "(" + dueDate + ")"; }
    }

    public static void main(String[] args) {
        listDemo();
        mapDemo();
        enumDemo();
        filterSortPageDemo();
    }

    // ---------- List: ordered, can grow, allows duplicates ----------
    static void listDemo() {
        System.out.println("== List ==");
        List<String> names = new ArrayList<>();
        names.add("Pay rent");                 // add at the end
        names.add("Buy milk");
        names.add("Call mom");
        System.out.println(names.get(0));      // get by index (0-based!)  -> Pay rent
        System.out.println(names.size());      // 3
        names.remove("Buy milk");              // remove by value
        names.remove(0);                       // remove by index
        for (String n : names) {               // loop
            System.out.println("  left: " + n);
        }
        System.out.println(names.isEmpty());   // false
        // Hand out a COPY so callers cannot change your private list (encapsulation):
        List<String> copy = new ArrayList<>(names);
        copy.clear();
        System.out.println(names.size() + " (original untouched)");
    }

    // ---------- Map: key -> value lookup, keys are unique ----------
    static void mapDemo() {
        System.out.println("== Map ==");
        Map<Integer, String> byId = new LinkedHashMap<>(); // LinkedHashMap keeps insertion order
        byId.put(1, "Pay rent");               // add / replace
        byId.put(2, "Buy milk");
        System.out.println(byId.get(2));       // lookup  -> Buy milk
        System.out.println(byId.get(99));      // missing key -> null (NOT an exception!)
        System.out.println(byId.containsKey(1));
        byId.remove(1);
        for (Map.Entry<Integer, String> e : byId.entrySet()) {   // loop over key + value
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }
        for (Integer key : byId.keySet()) { /* keys only */ }
        for (String value : byId.values()) { /* values only */ }
        // The idempotency store is exactly this: Map<String, Record> keyed by idempotencyKey.
    }

    // ---------- enum: parsing user input safely ----------
    static void enumDemo() {
        System.out.println("== enum ==");
        Priority p = Priority.HIGH;
        System.out.println(p + " " + p.name() + " " + p.ordinal());   // HIGH HIGH 2
        for (Priority each : Priority.values()) {                      // all values
            System.out.println("  " + each);
        }
        System.out.println(Priority.valueOf("LOW"));                   // text -> enum
        try {
            Priority.valueOf("URGENT");                                // not a constant
        } catch (IllegalArgumentException e) {
            System.out.println("  bad priority -> this is where you add a FieldIssue");
        }
        // valueOf is case-sensitive: "low" fails. Decide whether to accept it (trim/toUpperCase) and be consistent.
        System.out.println(p == Priority.HIGH);                        // compare enums with ==
    }

    // ---------- the four moves behind Step 7: filter -> search -> sort -> page ----------
    static void filterSortPageDemo() {
        System.out.println("== filter / sort / page ==");
        List<Item> all = new ArrayList<>();
        all.add(new Item("Pay rent", "2026-12-01"));
        all.add(new Item("Buy milk", null));
        all.add(new Item("Pay phone bill", "2026-11-20"));
        all.add(new Item("Call mom", "2026-11-15"));

        // filter: build a NEW list of the items you keep
        List<Item> matches = new ArrayList<>();
        for (Item item : all) {
            if (item.getTitle().toLowerCase().contains("pay")) {   // case-insensitive search
                matches.add(item);
            }
        }
        System.out.println("search 'pay': " + matches);

        // sort: a Comparator says how to order two items. ISO dates sort correctly as plain Strings.
        Comparator<Item> byDueDate = new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.getDueDate().compareTo(b.getDueDate());   // NPE if null - handle nulls yourself!
            }
        };
        Collections.sort(matches, byDueDate);                       // ascending
        System.out.println("by dueDate asc: " + matches);
        Collections.reverse(matches);                               // descending
        System.out.println("by dueDate desc: " + matches);

        // page: page 1 is the first page. start = (page - 1) * pageSize
        int page = 2;
        int pageSize = 3;
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        List<Item> pageItems = start >= all.size()
                ? new ArrayList<Item>()                             // page beyond the last page -> empty list
                : new ArrayList<Item>(all.subList(start, end));
        System.out.println("page 2 of size 3: " + pageItems);
    }
}
