/**
 * Copyright (C) 2017 Marvin Herman Froeder (marvin@marvinformatics.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
expected = ['/META-INF/MANIFEST.MF']

for (item in expected)
{
    def file = new File(basedir, 'target/dependency' + item)
    if (!file.exists())
    {
       throw new RuntimeException("Missing "+file.name);
    }
}

notExpected = ['/stylesheet.css']

for (item in notExpected)
{
    def file = new File(basedir, 'target/dependency' + item)    
    if (file.exists())
    {
       throw new RuntimeException("This file shouldn't be here: "+file.name);
    }
}

return true;
