
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

import java.util.List;

/**
 * Mixed integer model to assign individual students as well as teams of students to topics.
 */
@SuppressWarnings("serial")
public class TopicAssignment extends IloCplex {

    /* Model data */
    int           N;                 // number of students
    int           G;                 // number of groups
    int[]         Kg;                // number of students per group
    int           T;                 // number of topics
    int           S;                 // number of supervisors
    int[][]       Ts;                // Ts[s] = set of topic id-s of supervisor s
    int[]         Ls;                // Ls[s] = min number of topics of supervisor s
    int[]         Us;                // Us[s] = max number of topics of supervisor s
    int[][]       rs;                // rs[i][t] = rank of student i for topic t
    int[][]       rg;                // rg[i][t] = rank of group i for topic t

    /* Additional data for pretty output */
    String[]      supervisorNames;
    String[]      topicNames;
    String[]      studentNames;
    String[]      groupNames;

    /* Decision variables */
    IloNumVar[][] xs;                // i = 0, ..., N, t
    IloNumVar[][] xg;                // i = 0, ..., G; t
    IloNumVar[][] y;                 // t; n = 0, 1 (representing 2 and 3)

    /* Objective */
    IloObjective  objective;

    /* Constraints */
    IloRange[]    constrStudents;
    IloRange[]    constrGroups;
    IloRange[]    constrTopics;
    IloRange[]    constrTopicModes;
    IloRange      constrTopics3_once;
    IloRange[]    constrSupervisors;

    TopicAssignment() throws IloException {

        /* Model data */
        N = 4;
        G = 5;
        Kg = new int[] { 2, 2, 3, 2, 2 };
        T = 14;
        S = 7;
        Ts = new int[S][];
        Ts[0] = new int[] { 0, 1 };
        Ts[1] = new int[] { 2, 3 };
        Ts[2] = new int[] { 4, 5 };
        Ts[3] = new int[] { 6, 7 };
        Ts[4] = new int[] { 8, 9 };
        Ts[5] = new int[] { 10, 11 };
        Ts[6] = new int[] { 12, 13 };
        Ls = new int[] { 1, 1, 1, 1, 1, 1, 1 };
        Us = new int[] { 1, 1, 1, 1, 1, 1, 1 };

        List<String> lines = TextFileReader.readLines("data/ranking_students.csv");
        rs = new int[N][T];
        for (int i = 0; i < N; i++) {
            System.out.println("s " + i + ": " + lines.get(i));
            String[] splitted = lines.get(i).split("[,\\t]");
            for (int t = 0; t < T; t++)
                rs[i][t] = Integer.parseInt(splitted[t].trim());
        }
        lines = TextFileReader.readLines("data/ranking_teams.csv");
        rg = new int[G][T];
        for (int i = 0; i < G; i++) {
            System.out.println("g " + i + ": " + lines.get(i));
            String[] splitted = lines.get(i).split("[,\\t]");
            for (int t = 0; t < T; t++) {
                rg[i][t] = Integer.parseInt(splitted[t]);
            }
        }

        /* Additional data for output */
        lines = TextFileReader.readLines("data/supervisor_names.csv");
        supervisorNames = new String[S];
        for (int s = 0; s < S; s++) {
            String[] splitted = lines.get(s).split("[,\\t]");
            supervisorNames[s] = splitted[1];
        }
        lines = TextFileReader.readLines("data/topic_names.csv");
        topicNames = new String[T];
        for (int t = 0; t < T; t++) {
            String[] splitted = lines.get(t).split("[,\\t]");
            topicNames[t] = splitted[1];
        }
        lines = TextFileReader.readLines("data/student_names.csv");
        studentNames = new String[N];
        for (int i = 0; i < N; i++) {
            String[] splitted = lines.get(i).split("[,\\t]");
            studentNames[i] = splitted[1];
        }
        lines = TextFileReader.readLines("data/group_names.csv");
        groupNames = new String[G];
        for (int i = 0; i < G; i++) {
            String[] splitted = lines.get(i).split("[\\t]");
            groupNames[i] = splitted[1];
        }

        /* Model */
        // variables
        addVarsX();
        addVarsY();

        // objective
        addObjective();

        // constraints
        addConstraintsStudents();
        addConstraintsGroups();
        addConstraintsTopics();
        addConstraintsTopicModes();
        addConstraintsTopics3_once();
        addConstraintsSupervisors();

        /* Configuration */
        // these configuration settings are only relevant in large scale models
        // you can play with them to obtain better results
        setOut(null); // avoid CPLEX output
        setParam(IloCplex.Param.MIP.Strategy.File, 0); // do not write branch and bound nodes to hard drive
                                                       // crash if it would be necessary.
        setParam(IloCplex.Param.Parallel, -1); // opportunistic: less synchronization between threads and
                                               // consequently may provide better performance
                                               // but not deterministic!!!
        setParam(IloCplex.Param.Threads, 0); // takes what he can get
        setParam(IloCplex.Param.WorkMem, 4096); // increase working memory
    }

    void addVarsX() throws IloException {
        xs = new IloNumVar[N][T];
        for (int i = 0; i < N; i++)
            for (int t = 0; t < T; t++)
                xs[i][t] = boolVar("xs_" + i + "," + t);

        xg = new IloNumVar[G][T];
        for (int i = 0; i < G; i++)
            for (int t = 0; t < T; t++)
                xg[i][t] = boolVar("xg_" + i + "," + t);

    }

    void addVarsY() throws IloException {
        y = new IloNumVar[T][2];
        for (int t = 0; t < T; t++)
            for (int n = 0; n < 2; n++)
                y[t][n] = boolVar("y_" + t + "," + n);
    }

    void addObjective() throws IloException {
        objective = addMinimize();
        IloLinearNumExpr expr = linearNumExpr();

        for (int i = 0; i < N; i++)
            for (int t = 0; t < T; t++)
                expr.addTerm(Math.pow(rs[i][t], 2), xs[i][t]);

        for (int i = 0; i < G; i++)
            for (int t = 0; t < T; t++)
                expr.addTerm(Math.pow(rg[i][t], 2) * Kg[i], xg[i][t]);

        objective.setExpr(expr);
    }

    void addConstraintsStudents() throws IloException {
        constrStudents = new IloRange[N];
        for (int i = 0; i < N; i++) {
            IloLinearNumExpr expr = linearNumExpr();
            for (int t = 0; t < T; t++)
                expr.addTerm(1.0, xs[i][t]);
            constrStudents[i] = addEq(expr, 1.0, "assign_s_" + i);
        }
    }

    void addConstraintsGroups() throws IloException {
        constrGroups = new IloRange[G];
        for (int i = 0; i < G; i++) {
            IloLinearNumExpr expr = linearNumExpr();
            for (int t = 0; t < T; t++)
                expr.addTerm(1.0, xg[i][t]);
            constrGroups[i] = addEq(expr, 1.0, "assign_g_" + i);
        }
    }

    void addConstraintsTopics() throws IloException {
        constrTopics = new IloRange[T];
        for (int t = 0; t < T; t++) {
            IloLinearNumExpr expr = linearNumExpr();
            for (int i = 0; i < N; i++)
                expr.addTerm(1.0, xs[i][t]);
            for (int i = 0; i < G; i++)
                expr.addTerm(Kg[i], xg[i][t]);
            for (int n = 0; n < 2; n++)
                expr.addTerm(-(2.0 + n), y[t][n]);
            constrTopics[t] = addEq(expr, 0.0, "topics_" + t);
        }
    }

    void addConstraintsTopicModes() throws IloException {
        constrTopicModes = new IloRange[T];
        for (int t = 0; t < T; t++) {
            IloLinearNumExpr expr = linearNumExpr();
            for (int n = 0; n < 2; n++)
                expr.addTerm(1.0, y[t][n]);
            constrTopicModes[t] = addLe(expr, 1.0, "topic_modes_" + t);
        }
    }

    void addConstraintsTopics3_once() throws IloException {
        IloLinearNumExpr expr = linearNumExpr();
        for (int t = 0; t < T; t++)
            expr.addTerm(1.0, y[t][1]);
        constrTopics3_once = addLe(expr, 1.0, "topics_3_once");
    }

    void addConstraintsSupervisors() throws IloException {
        constrSupervisors = new IloRange[S];
        for (int s = 0; s < S; s++) {
            IloLinearNumExpr expr = linearNumExpr();
            for (int t : Ts[s])
                for (int n = 0; n < 2; n++)
                    expr.addTerm(1.0, y[t][n]);
            constrSupervisors[s] = addRange(Ls[s], expr, Us[s], "supervisor_" + s);
        }
    }

    void printSol() throws UnknownObjectException, IloException {
        System.out.println("\nVal = " + getObjValue());

        System.out.println("\nSingles");
        for (int i = 0; i < N; i++) {
            for (int t = 0; t < T; t++) {
                double val = getValue(xs[i][t]);
                if ((int) Math.round(val) == 1) {
                    System.out.println(String.format("%-40s", studentNames[i]) + " --> " + topicNames[t]
                            + " (rank = " + rs[i][t] + ")");

                }
            }
        }
        System.out.println("\nGroups");
        for (int i = 0; i < G; i++) {
            for (int t = 0; t < T; t++) {
                double val = getValue(xg[i][t]);
                if ((int) Math.round(val) == 1) {
                    System.out.println(String.format("%-40s", groupNames[i]) + " --> " + topicNames[t]
                            + " (rank = " + rg[i][t] + ")");
                }
            }
        }
        System.out.println("\nSupervisors");
        for (int s = 0; s < S; s++) {
            int studentCount = 0;
            int groupCount = 0;
            for (int t : Ts[s]) {
                for (int i = 0; i < G; i++)
                    if ((int) Math.round(getValue(xg[i][t])) == 1) groupCount++;
                for (int i = 0; i < N; i++)
                    if ((int) Math.round(getValue(xs[i][t])) == 1) studentCount++;
            }
            System.out.println(
                    String.format("%-40s", supervisorNames[s]) + " --> " + studentCount + " students, " + groupCount + " groups");
        }
    }

    public static void main(String[] args) throws IloException {
        TopicAssignment model = new TopicAssignment();
        model.solve();
        model.printSol();
    }
}
