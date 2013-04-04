/* CpuTests.java
 *
 * Tests for the EduMIPS64 CPU. These test focus on running MIPS64 programs,
 * treating the CPU as a black box and analyzing the correctness of its
 * outputs.
 *
 * (c) 2012-2013 Andrea Spadaccini
 *
 * This file is part of the EduMIPS64 project, and is released under the GNU
 * General Public License.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.edumips64.tests;

import org.edumips64.core.*;
import org.edumips64.core.is.*;
import org.edumips64.ui.CycleBuilder;
import org.edumips64.utils.Config;

import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class CpuTests {
  protected CPU cpu;
  protected Parser parser;
  public static String testsLocation = "src/org/edumips64/tests/data/";
  private final static Logger log = Logger.getLogger(CpuTestStatus.class.getName());

  /** Class that holds the parts of the CPU status that need to be tested
   * after the execution of a test case.
   */
  class CpuTestStatus {
    int cycles;
    int rawStalls, wawStalls, memStalls;

    public CpuTestStatus(CPU cpu) {
      cycles = cpu.getCycles();
      wawStalls = cpu.getWAWStalls();
      rawStalls = cpu.getRAWStalls();
      memStalls = cpu.getStructuralStallsMemory();

      log.warning("Got " + cycles + " cycles, " + rawStalls + " RAW Stalls and " + wawStalls + " WAW stalls.");
    }
  }

  /** Class to hold the FPU exceptions configuration.
   */
  class FPUExceptionsConfig {
    boolean invalidOperation, overflow, underflow, divideByZero;

    // Constructor, initializes the values from the Config store.
    public FPUExceptionsConfig() {
      invalidOperation = Config.getBoolean("INVALID_OPERATION");
      overflow = Config.getBoolean("OVERFLOW");
      underflow = Config.getBoolean("UNDERFLOW");
      divideByZero = Config.getBoolean("DIVIDE_BY_ZERO");
    }

    // Restore values to the config Store.
    public void restore() {
      Config.putBoolean("INVALID_OPERATION", invalidOperation);
      Config.putBoolean("OVERFLOW", overflow);
      Config.putBoolean("UNDERFLOW", underflow);
      Config.putBoolean("DIVIDE_BY_ZERO", divideByZero);
    }
  }

  protected FPUExceptionsConfig fec;

  @BeforeClass
  public static void setup() {
    // Disable logs of level lesser than WARNING.
    Logger rootLogger = log.getParent();

    for (Handler h : rootLogger.getHandlers()) {
      h.setLevel(java.util.logging.Level.SEVERE);
    }
  }

  @Before
  public void testSetup() {
    cpu = CPU.getInstance();
    cpu.setStatus(CPU.CPUStatus.READY);
    parser = Parser.getInstance();
    Instruction.setEnableForwarding(true);
    fec = new FPUExceptionsConfig();
  }

  @After
  public void testTearDown() {
    fec.restore();
  }

  /** Executes a MIPS64 program, raising an exception if it does not
   * succeed.
   *
   * @param testPath path of the test code.
   */
  protected CpuTestStatus runMipsTest(String testPath) throws Exception {
    log.warning("================================= Starting test " + testPath);
    cpu.reset();
    testPath = testsLocation + testPath;
    CycleBuilder builder = new CycleBuilder();

    try {
      try {
        parser.parse(testPath);
      } catch (ParserMultiWarningException e) {
        // This exception is raised even if there are only warnings.
        // We must raise it only if there are actual errors.
        if (e.hasErrors()) {
          throw e;
        }
      }

      cpu.setStatus(CPU.CPUStatus.RUNNING);

      while (true) {
        cpu.step();
        builder.step();
      }
    } catch (HaltException e) {
      CpuTestStatus cts = new CpuTestStatus(cpu);
      log.warning("================================= Finished test " + testPath);
      return cts;
    } finally {
      cpu.reset();
    }
  }

  /** Runs a MIPS64 test program with and without forwarding, raising an
   *  exception if it does not succeed.
   *
   * @param testPath path of the test code.
   * @return a dictionary that maps the forwarding status to the
   * corresponding CpuTestStatus object.
   */
  protected Map<Boolean, CpuTestStatus> runMipsTestWithAndWithoutForwarding(String testPath) throws Exception {
    boolean forwardingStatus = Instruction.getEnableForwarding();
    Map<Boolean, CpuTestStatus> statuses = new HashMap<Boolean, CpuTestStatus>();

    Instruction.setEnableForwarding(true);
    statuses.put(true, runMipsTest(testPath));

    Instruction.setEnableForwarding(false);
    statuses.put(false, runMipsTest(testPath));

    Instruction.setEnableForwarding(forwardingStatus);
    return statuses;
  }

  private void runForwardingTest(String path, int cycles_with_forwarding,
                                 int cycles_without_forwarding) throws Exception {
    Map<Boolean, CpuTestStatus> statuses = runMipsTestWithAndWithoutForwarding(path);

    assertEquals(cycles_with_forwarding, statuses.get(true).cycles);
    assertEquals(cycles_without_forwarding, statuses.get(false).cycles);
  }


  /* Test for the instruction BREAK */
  @Test(expected = BreakException.class)
  public void testBREAK() throws Exception {
    runMipsTest("break.s");
  }

  /* Test for r0 */
  @Test
  public void testR0() throws Exception {
    runMipsTest("zero.s");
  }

  /* Test for instruction B */
  @Test
  public void testB() throws Exception {
    runMipsTest("b.s");
  }

  /* Test for the instruction JAL */
  @Test
  public void testJAL() throws Exception {
    runMipsTest("jal.s");
  }

  /* Test for utils/strlen.s */
  @Test
  public void testStrlen() throws Exception {
    runMipsTest("test-strlen.s");
  }

  /* Test for utils/strcmp.s */
  @Test
  public void testStrcmp() throws Exception {
    runMipsTest("test-strcmp.s");
  }

  /* Tests for the memory */
  @Test
  public void testMemory() throws Exception {
    runMipsTest("memtest.s");
  }

  /* Forwarding test. The number of cycles is hardcoded and depends on the
   * contents of forwarding.s */
  @Test
  public void testForwarding() throws Exception {
    CpuTestStatus temp;

    // Simple test.
    runForwardingTest("forwarding.s", 16, 19);

    // Tests taken from Hennessy & Patterson, Appendix A
    runForwardingTest("forwarding-hp-pA16.s", 11, 13);
    runForwardingTest("forwarding-hp-pA18.s", 9, 13);
  }

  @Test
  public void storeAfterLoad() throws Exception {
    runMipsTest("store-after-load.s");
  }

  /* ------- FPU TESTS -------- */
  @Test
  public void testFPUStalls() throws Exception {
    Map<Boolean, CpuTestStatus> statuses = runMipsTestWithAndWithoutForwarding("fpu-waw.s");

    // With forwarding
    assertEquals(20, statuses.get(true).cycles);
    assertEquals(7, statuses.get(true).wawStalls);
    assertEquals(1, statuses.get(true).rawStalls);

    // Without forwarding
    assertEquals(21, statuses.get(false).cycles);
    assertEquals(7, statuses.get(false).wawStalls);
    assertEquals(2, statuses.get(false).rawStalls);
  }

  @Test
  public void testFPUMul() throws Exception {
    // This test contains code that raises exceptions, let's disable them.
    Config.putBoolean("INVALID_OPERATION", false);
    Config.putBoolean("OVERFLOW", false);
    Config.putBoolean("UNDERFLOW", false);
    Config.putBoolean("DIVIDE_BY_ZERO", false);
    Map<Boolean, CpuTestStatus> statuses = runMipsTestWithAndWithoutForwarding("fpu-mul.s");

    // Same behaviour with and without forwarding.
    assertEquals(43, statuses.get(true).cycles);
    assertEquals(43, statuses.get(false).cycles);
    assertEquals(6, statuses.get(true).memStalls);
    assertEquals(6, statuses.get(false).memStalls);
  }

  /* ------- REGRESSION TESTS -------- */
  /* Issue #7 */
  @Test
  public void testMovnIssue7() throws Exception {
    runMipsTest("movn-issue-7.s");
  }

  @Test
  public void testMovzIssue7() throws Exception {
    runMipsTest("movz-issue-7.s");
  }

  /* Issue #2: Misaligned memory operations are not handled correctly */
  @Test(expected = NotAlignException.class)
  public void testMisalignLD() throws Exception {
    runMipsTest("misaligned-ld.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignSD() throws Exception {
    runMipsTest("misaligned-sd.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignLW() throws Exception {
    runMipsTest("misaligned-lw.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignLWU() throws Exception {
    runMipsTest("misaligned-lwu.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignSW() throws Exception {
    runMipsTest("misaligned-sw.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignLH() throws Exception {
    runMipsTest("misaligned-lh.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignLHU() throws Exception {
    runMipsTest("misaligned-lhu.s");
  }

  @Test(expected = NotAlignException.class)
  public void testMisalignSH() throws Exception {
    runMipsTest("misaligned-sh.s");
  }

  @Test
  public void testAligned() throws Exception {
    runMipsTest("aligned.s");
  }
}
