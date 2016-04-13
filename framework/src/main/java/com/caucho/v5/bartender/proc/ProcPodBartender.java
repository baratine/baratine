/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.bartender.proc;

import java.io.IOException;

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.files.FileStatusImpl;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodBartender.PodType;
import com.caucho.v5.io.WriteStream;

import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.service.Result;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcPodBartender extends ProcFileBase
{
  private final PodBartender _pod;

  public ProcPodBartender(PodBartender pod)
  {
    super(pod.getId());

    _pod = pod;
  }

  @Override
  protected boolean fillRead(WriteStream out)
    throws IOException
  {
    PodBartender pod = _pod;

    return fillRead(pod, out);
  }

  @Override
  public void getStatus(Result<Status> result)
  {
    String path = _pod.name();
    Status.FileType type = Status.FileType.FILE;
    long length = 0;
    long version = 0;
    long modifiedTime = 0;
    long checkSum = 0;

    result.ok(new FileStatusImpl(path, type, length, version,
                                       modifiedTime, checkSum, null));
  }

  static boolean fillRead(PodBartender pod, WriteStream out)
    throws IOException
  {
    out.println("{ \"pod\" : \"" + pod.name() + "." + pod.getClusterId() + "\",");
    out.println("  \"type\" : \"" + pod.getType() + "\",");
    out.print("  \"sequence\" : " + pod.getSequence());
    
    if (PodType.off.equals(pod.getType())) {
      out.println("\n}");
      return true;
    }
    
    out.print(",\n  \"servers\" : [");

    int count = pod.serverCount();

    for (int i = 0; i < count; i++) {
      if (i > 0) {
        out.print(",");
      }

      ServerBartender server = pod.server(i);

      if (server != null) {
        out.print("\n    \"" + server.getId() + "\"");
      }
      else {
        out.print("\n    \"\"");
      }
    }
    out.println();
    out.println("  ],");

    out.print("  \"nodes\" : [");

    int nodeCount = pod.nodeCount();
    for (int i = 0; i < nodeCount; i++) {
      if (i > 0) {
        out.print(",");
      }

      NodePodAmp node = pod.getNode(i);
      out.print("\n    [");

      for (int j = 0; j < pod.getDepth(); j++) {
        if (j != 0) {
          out.print(", ");
        }

        out.print(node.owner(j));
      }

      out.print("]");
    }
    out.println();
    out.println("  ],");

    out.print("  \"vnodes\" : [");

    for (int i = 0; i < nodeCount; i++) {
      if (i > 0) {
        out.print(",");
      }

      out.print("\n    [");

      int vnodeCount = 0;
      for (int j = 0; j < pod.getVnodeCount(); j++) {
        if (pod.getNode(j).nodeIndex() == i) {
          if (vnodeCount > 0) {
            out.print(", ");

            if (vnodeCount % 10 == 0) {
              out.print("\n     ");
            }
          }
          vnodeCount++;

          out.print(j);
        }
      }

      out.print("]");
    }

    out.println();
    out.println("  ]");

    out.print("}");

    return true;
  }
}
