/*
Copyright 2011-2013 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package main

import (
	"fmt"
	"kanzi/transform"
	"math/rand"
	"os"
	"time"
)

func main() {
	fmt.Printf("\nTestBWT")
	TestCorrectness()
	TestSpeed()
}

func TestCorrectness() {
	fmt.Printf("\n\nCorrectness test")

	// Test behavior
	for ii := 0; ii < 20; ii++ {
		fmt.Printf("\nTest %v\n", ii)
		rnd := rand.New(rand.NewSource(time.Now().UnixNano()))

		size := uint(0)
		var buf1 []byte
		var strbuf []byte

		if ii == 0 {
			size = 0
			buf1 = []byte{'m', 'i', 's', 's', 'i', 's', 's', 'i', 'p', 'p', 'i'}
		} else {
			size = 128
			buf1 = make([]byte, size)

			for i := 0; i < len(buf1); i++ {
				buf1[i] = byte(65 + rnd.Intn(4*ii))
			}

			buf1[len(buf1)-1] = byte(0)
		}

		strbuf = make([]byte, len(buf1))

		for k := range buf1 {
			strbuf[k] = buf1[k]
		}

		bwt, _ := transform.NewBWT(size)
		str1 := string(strbuf)
		fmt.Printf("Input:   %s\n", str1)
		buf2 := bwt.Forward(buf1)
		primaryIndex := bwt.PrimaryIndex()

		for k := range buf2 {
			strbuf[k] = buf2[k]
		}

		str2 := string(strbuf)
		fmt.Printf("Encoded: %s", str2)
		fmt.Printf("  (Primary index=%v)\n", bwt.PrimaryIndex())
		bwt.SetPrimaryIndex(primaryIndex)
		buf3 := bwt.Inverse(buf2)

		for k := range buf3 {
			strbuf[k] = buf3[k]
		}

		str3 := string(strbuf)
		fmt.Printf("Output:  %s\n", str3)

		if str1 == str3 {
			fmt.Printf("Identical\n")
		} else {
			fmt.Printf("Different\n")
			os.Exit(1)
		}
	}
}

func TestSpeed() {
	fmt.Printf("\nSpeed test")
	iter := 2000
	size := 256 * 1024
	delta1 := int64(0)
	delta2 := int64(0)
	buf1 := make([]byte, size)
	buf2 := make([]byte, size)
	fmt.Printf("\nIterations: %v", iter)
	fmt.Printf("\nTransform size: %v\n", size)


	for jj := 0; jj < 3; jj++ {
		bwt, _ := transform.NewBWT(0)
		rnd := rand.New(rand.NewSource(time.Now().UnixNano()))

		for i := 0; i < iter; i++ {
			for i := range buf1 {
				buf1[i] = byte(rnd.Intn(255) + 1)
			}

			buf1[size-1] = 0
			copy(buf2, buf1)
			before := time.Now()
			bwt.Forward(buf2)
			after := time.Now()
			delta1 += after.Sub(before).Nanoseconds()
			before = time.Now()
			bwt.Inverse(buf2)
			after = time.Now()
			delta2 += after.Sub(before).Nanoseconds()

			// Sanity check
			for i := range buf1 {
				if buf1[i] != buf2[i] {
					println("Error at index %v: %v<->%v\n", i, buf1[i], buf2[i])
					os.Exit(1)
				}
			}
		}

		println()
		prod := int64(iter) * int64(size)
		fmt.Printf("BWT Forward transform [ms]: %v\n", delta1/1000000)
		fmt.Printf("Throughput [KB/s]         : %d\n", prod*1000000/delta1*1000/1024)
		fmt.Printf("BWT Inverse transform [ms]: %v\n", delta2/1000000)
		fmt.Printf("Throughput [KB/s]         : %d\n", prod*1000000/delta1*1000/1024)
		println()
	}
}